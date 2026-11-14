package com.lucasmanoel.idiomando.infra;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova que o papel de producao (infra/sql) so le e respeita o timeout, rodando os mesmos SQL que a VPS roda.
 * Sem Spring: o que pode quebrar aqui sao as permissoes, nao o app.
 */
@Testcontainers
class PapelDoAppTest {

    private static final String SENHA = "senha-do-teste";

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16")
            .withCopyFileToContainer(MountableFile.forHostPath("infra/sql/criar-papel-app.sql"), "/sql/criar-papel-app.sql")
            .withCopyFileToContainer(MountableFile.forHostPath("infra/sql/permissoes-app.sql"), "/sql/permissoes-app.sql");

    @BeforeAll
    static void prepararBanco() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();
        executarSql("/sql/criar-papel-app.sql", "-v", "senha=" + SENHA);
        executarSql("/sql/permissoes-app.sql");
    }

    private static void executarSql(String arquivo, String... variaveis) throws Exception {
        List<String> comando = new ArrayList<>(List.of("psql", "-U", postgres.getUsername(),
                "-d", postgres.getDatabaseName(), "-v", "ON_ERROR_STOP=1"));
        comando.addAll(List.of(variaveis));
        comando.addAll(List.of("-f", arquivo));
        var resultado = postgres.execInContainer(comando.toArray(new String[0]));
        assertThat(resultado.getExitCode()).as(resultado.getStderr()).isZero();
    }

    private static Connection comoApp() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), "idiomando_app", SENHA);
    }

    private static String mostrar(Connection c, String parametro) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW " + parametro)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    void deveLerAsTabelas() throws Exception {
        try (Connection c = comoApp(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM ocorrencia")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong(1)).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO video (youtube_id, titulo, idioma) VALUES ('abcdefghijk', 't', 'en')",
            "DROP TABLE trecho",
            "SELECT nextval('ocorrencia_seq')"})
    void naoDeveGravarNemApagar(String sql) throws Exception {
        try (Connection c = comoApp(); Statement st = c.createStatement()) {
            // 25006 = transacao somente leitura (default_transaction_read_only); 42501 = sem permissao
            assertThatThrownBy(() -> st.execute(sql))
                    .isInstanceOfSatisfying(SQLException.class,
                            e -> assertThat(e.getSQLState()).isIn("25006", "42501"));
        }
    }

    @Test
    void deveTerTimeoutDe3s() throws Exception {
        try (Connection c = comoApp(); Statement st = c.createStatement()) {
            assertThat(mostrar(c, "statement_timeout")).isEqualTo("3s");
            assertThatThrownBy(() -> st.execute("SELECT pg_sleep(4)"))
                    .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("57014"));
        }
    }

    /** O orcamento da busca (BuscaRepository.limitarTempo/restaurarTempo) tem que funcionar com o papel somente leitura. */
    @Test
    void deveVoltarAoTimeoutDoPapelDepoisDoOrcamentoDaBusca() throws Exception {
        try (Connection c = comoApp()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement("SELECT set_config('statement_timeout', ?, true)")) {
                ps.setString(1, "2500");
                ps.executeQuery();
            }
            assertThat(mostrar(c, "statement_timeout")).isEqualTo("2500ms");
            try (Statement st = c.createStatement()) {
                st.execute("SET LOCAL statement_timeout TO DEFAULT");
            }
            assertThat(mostrar(c, "statement_timeout")).isEqualTo("3s");
            c.rollback();
        }
    }

    @Test
    void deveEnxergarASequenceParaOValidate() throws Exception {
        try (Connection c = comoApp(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM information_schema.sequences WHERE sequence_name = 'ocorrencia_seq'")) {
            assertThat(rs.next()).isTrue();
        }
    }
}
