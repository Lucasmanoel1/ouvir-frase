# Idiomando: operação em produção

Roteiro para colocar o site no ar numa VPS do zero, publicar versões novas, voltar atrás e atualizar o índice. É para copiar e colar: cada passo termina com **Confira**, que diz o que deve aparecer. Se não aparecer, pare ali.

**Como ler**
- `[PC]` roda no seu computador (PowerShell, na pasta do repositório). `[VPS]` roda na VPS, por SSH.
- Troque os marcadores: `DOMINIO` (ex.: `idiomando.com.br`), `USUARIO` (seu usuário na VPS) e `IP_DA_VPS`.
- Os arquivos usados aqui estão em `infra/` (compose de produção, SQL, scripts e Nginx). Na VPS, a pasta `/opt/idiomando` guarda o conteúdo dela, menos o `nginx/`.
- Versão testada: Ubuntu Server 24.04 LTS, Nginx 1.24 (o do Ubuntu), Docker Engine com Compose v2.

---

## 1. O que existe em produção

```
internet -> Nginx (443, HTTPS) -> app (127.0.0.1:8080) -> db (rede interna do Compose)
```

- Só o Nginx escuta em IP público (22, 80 e 443). O app publica só em `127.0.0.1`; o banco não publica porta nenhuma.
- O app roda com o profile `prod`: conecta como `idiomando_app` (só lê, com limite de 3 s por consulta) e não roda o Flyway.
- **Não existem em produção:** o painel de ingestão, a ingestão e a `YOUTUBE_API_KEY`. O YouTube bloqueia IP de nuvem; você ingere no seu computador e leva o banco pronto (seção 15).
- Sem monitor externo e sem e-mail de alerta (decisão do projeto): se o site cair, você só vê ao abrir o site ou o painel da Hostinger. Por isso a rotina mensal (seção 16) existe.

---

## 2. Comprar e preparar

1. **VPS:** Hostinger KVM 1 (1 vCPU, 4 GB, NVMe), Ubuntu Server 24.04 LTS, data center no Brasil se houver. Anote o IP.
2. **Domínio:** `.com.br` no Registro.br.
3. **DNS** (no Registro.br, DNS do próprio Registro.br), TTL 300 s:

   | Tipo | Nome | Valor |
   |---|---|---|
   | A | `DOMINIO` | `IP_DA_VPS` |
   | A | `www` | `IP_DA_VPS` |
   | AAAA | `DOMINIO` e `www` | o IPv6 da VPS, **só se** ela tiver um |

**Confira** `[PC]` (pode levar alguns minutos):
```powershell
nslookup DOMINIO
nslookup www.DOMINIO
```
Os dois devolvem o `IP_DA_VPS`.

---

## 3. Primeiro acesso e SSH

**3.1 Chave no seu computador** `[PC]`. Se já existir `id_ed25519`, pule o primeiro comando.
```powershell
ssh-keygen -t ed25519 -C "idiomando-vps"
Get-Content $env:USERPROFILE\.ssh\id_ed25519.pub
```
Copie a linha que o segundo comando mostra (começa com `ssh-ed25519`). Nunca copie o arquivo sem `.pub`.

**3.2 Criar seu usuário** `[VPS]`. Entre como `root` (com a senha ou a chave que a Hostinger deu):
```powershell
ssh root@IP_DA_VPS
```
```bash
adduser USUARIO
usermod -aG sudo USUARIO
install -d -m 700 -o USUARIO -g USUARIO /home/USUARIO/.ssh
echo "COLE_AQUI_A_LINHA_DA_CHAVE_PUBLICA" >> /home/USUARIO/.ssh/authorized_keys
chown USUARIO:USUARIO /home/USUARIO/.ssh/authorized_keys
chmod 600 /home/USUARIO/.ssh/authorized_keys
```
**Confira** `[PC]`, numa janela nova (deixe a do `root` aberta):
```powershell
ssh USUARIO@IP_DA_VPS
```
Entra sem pedir senha de login. Na VPS, `sudo whoami` responde `root` (pede a senha do usuário).

**3.3 Desligar senha e login de root** `[VPS]`, como `USUARIO`:
```bash
sudo tee /etc/ssh/sshd_config.d/00-idiomando.conf <<'EOF'
PasswordAuthentication no
PermitRootLogin no
KbdInteractiveAuthentication no
EOF
sudo sshd -t && echo "configuracao ok"
sudo sshd -T | grep -iE "^(passwordauthentication|permitrootlogin|kbdinteractiveauthentication)"
```
O nome começa com `00-` de propósito: o `sshd` vale o **primeiro** valor que lê, e imagens de VPS costumam trazer um `50-cloud-init.conf` com `PasswordAuthentication yes`.

**Confira:** `configuracao ok` e as três linhas do `sshd -T` com `no`. Se alguma estiver `yes`, procure o arquivo que a define: `sudo grep -ri passwordauthentication /etc/ssh/sshd_config.d/`.

Só então reinicie, **com a sessão antiga ainda aberta**:
```bash
sudo systemctl restart ssh
```
**Confira** `[PC]`, em janela nova:
```powershell
ssh USUARIO@IP_DA_VPS                              # entra (chave)
ssh -o PubkeyAuthentication=no USUARIO@IP_DA_VPS   # recusa: Permission denied (publickey)
ssh root@IP_DA_VPS                                 # recusa
```
Se a primeira falhar, conserte pela sessão que ficou aberta. Só feche as antigas depois que as três respostas estiverem certas.

---

## 4. Firewall, fail2ban, atualizações e swap

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```
O `ufw enable` avisa que pode derrubar conexões SSH; como o SSH já está liberado na linha de cima, responda `y`.

**Confira:** `Status: active`, `Default: deny (incoming)` e só 22, 80 e 443 liberadas.

```bash
sudo apt update && sudo apt install -y fail2ban
sudo fail2ban-client status sshd
```
**Confira:** a jaula `sshd` aparece (`Currently banned: 0`). No Ubuntu ela já vem ligada.

```bash
cat /etc/apt/apt.conf.d/20auto-upgrades
systemctl status unattended-upgrades --no-pager | head -3
```
**Confira:** as duas linhas do arquivo com `"1"` e o serviço `active`. É o que instala as correções de segurança sozinho.

Swap de 2 GB (sem ele, um pico de memória mata o Postgres ou a JVM). Pule se `swapon --show` já listar algo:
```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```
**Confira:** `free -h` mostra 2,0 Gi na linha `Swap`.

Repita 22, 80 e 443 no **firewall do painel da Hostinger**, como segunda camada.

> **Docker × ufw.** Porta publicada pelo Docker (`ports:`) passa **por fora** das regras do `ufw`. Por isso o banco não publica porta nenhuma e o app publica só em `127.0.0.1`. Não troque isso no compose.

---

## 5. Docker

Repositório oficial da Docker para Ubuntu:
```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
```
Saia e entre de novo (`exit`, depois `ssh USUARIO@IP_DA_VPS`) para o grupo valer.

**Confira:** `docker run --rm hello-world` imprime `Hello from Docker!` e `docker compose version` mostra a versão 2.

---

## 6. Arquivos do app

Antes desta seção, a imagem tem que existir no GHCR: faça os passos 1 a 3 da seção 13 com a tag `v0.1.0`. Na primeira vez, torne o pacote **público** (GitHub -> Packages -> `idiomando` -> Package settings -> Change visibility -> Public), senão a VPS precisaria de um token para baixar a imagem.

**6.1 Pasta na VPS** `[VPS]`:
```bash
sudo mkdir -p /opt/idiomando && sudo chown $USER: /opt/idiomando
```

**6.2 Copiar `infra/`** `[PC]`, na pasta do repositório. A lista é explícita de propósito: `infra\*` não copia o `.env.exemplo` (arquivo com ponto) e o `nginx\` não vai para esta pasta.
```powershell
scp -r infra\docker-compose.yml infra\.env.exemplo infra\sql infra\scripts USUARIO@IP_DA_VPS:/opt/idiomando/
```

**6.3 Preparar o `.env`** `[VPS]`. O `scp` vindo do Windows traz fim de linha CRLF e tira a permissão de execução; os dois primeiros comandos corrigem isso. O `sed` com `openssl` coloca duas senhas longas e diferentes:
```bash
cd /opt/idiomando
chmod +x scripts/*.sh
cp .env.exemplo .env
sed -i 's/\r$//' .env docker-compose.yml sql/*.sql
sed -i "s|^POSTGRES_SENHA=.*|POSTGRES_SENHA=$(openssl rand -base64 24 | tr -d '/+=')|" .env
sed -i "s|^IDIOMANDO_DB_SENHA=.*|IDIOMANDO_DB_SENHA=$(openssl rand -base64 24 | tr -d '/+=')|" .env
chmod 600 .env
```
**Confira:**
```bash
ls -l .env                 # -rw------- 1 USUARIO USUARIO
grep -c troque .env        # 0
grep -c $'\r' .env         # 0
sed 's/=.*/=.../' .env     # mostra VERSAO=..., POSTGRES_SENHA=... e IDIOMANDO_DB_SENHA=... sem revelar valores
```
Guarde as duas senhas no seu gerenciador de senhas (`cat .env`). **Nunca** ponha `YOUTUBE_API_KEY` neste arquivo: a ingestão não roda aqui.

Se o pacote do GHCR ficasse privado, faltaria o login: `docker login ghcr.io -u Lucasmanoel1` com um token clássico só com `read:packages`. Com o pacote público, não precisa.

---

## 7. Banco

**7.1 Subir o banco** `[VPS]`:
```bash
cd /opt/idiomando
docker compose up -d db
docker compose ps
```
**Confira:** `db` com `healthy` (pode levar uns 20 s; repita o `ps`).

**7.2 Criar o papel do app** (uma vez por servidor):
```bash
set -a; . ./.env; set +a
docker compose exec -T db psql -U postgres -v senha="$IDIOMANDO_DB_SENHA" -f /sql/criar-papel-app.sql
```
**Confira:** `CREATE ROLE`, `ALTER ROLE`, `ALTER ROLE`.

**7.3 Primeira carga do índice.** Gere o dump no seu computador `[PC]`. Dentro do contêiner, porque o `>` do PowerShell corrompe arquivo binário:
```powershell
docker compose exec db pg_dump -U postgres -d idiomando -Fc -f /tmp/idiomando.dump
docker compose cp db:/tmp/idiomando.dump .\idiomando-AAAA-MM-DD.dump
docker compose exec db rm /tmp/idiomando.dump
docker compose exec db psql -U postgres -d idiomando -tA -c "SELECT (SELECT count(*) FROM video) || ' videos, ' || (SELECT count(*) FROM ocorrencia) || ' ocorrencias'"
```
Anote a contagem. O `.dump` é ignorado pelo Git. Envie para a VPS:
```powershell
scp .\idiomando-AAAA-MM-DD.dump USUARIO@IP_DA_VPS:/opt/idiomando/
```
Na VPS `[VPS]`. Só na primeira carga, crie o contêiner do app antes (sem iniciar), para o script poder parar e subir o app:
```bash
cd /opt/idiomando
docker compose pull app
docker compose create app
./scripts/atualizar-indice.sh idiomando-AAAA-MM-DD.dump
```
O script restaura num banco novo, aplica as permissões, roda `ANALYZE`, mostra as contagens do banco atual e do novo e pergunta `Trocar? (s/N)`. Na primeira vez o banco "atual" está vazio, e a linha dele mostra `relation "video" does not exist`: é esperado. Confira que o **novo** bate com a contagem que você anotou e responda `s`. O script troca os bancos, sobe o app e termina com `App no ar` e a lista de idiomas.

Depois apague o dump da VPS para liberar espaço: `rm idiomando-AAAA-MM-DD.dump`.

**Confira o papel do app** (nenhum dos dois comandos pode gravar algo; o `INSERT` é malformado de propósito):
```bash
set -a; . ./.env; set +a
docker compose exec -T -e PGPASSWORD="$IDIOMANDO_DB_SENHA" db psql -h localhost -U idiomando_app -d idiomando -c "SELECT count(*) FROM video" -c "SHOW statement_timeout" -c "INSERT INTO video (id) VALUES (-1)"
```
O `count` bate com o que você anotou, o timeout é `3s` e o `INSERT` falha com `cannot execute INSERT in a read-only transaction`.

---

## 8. App

Na primeira carga, o script do passo 7.3 já subiu o app; aqui você confirma. Em outro momento, é o que sobe o app.
```bash
cd /opt/idiomando
docker compose pull app
docker compose up -d app
docker compose ps
```
**Confira:**
```bash
curl -s 127.0.0.1:8080/actuator/health        # {"status":"UP"}
curl -s 127.0.0.1:8080/api/idiomas            # ["en","es","ru"] (os idiomas indexados)
docker inspect --format '{{.Name}} {{.HostConfig.LogConfig.Type}}' $(docker compose ps -q)   # os dois com "local"
```
Se o `health` não subir em 1 minuto: `docker compose logs app | tail -40`. Erros comuns: `Could not resolve placeholder 'IDIOMANDO_DB_SENHA'` (variável faltando no `.env`) e `password authentication failed` (a senha do `.env` não é a que você usou no passo 7.2). Para alinhar o papel com o `.env`:
```bash
set -a; . ./.env; set +a
echo "ALTER ROLE idiomando_app PASSWORD :'senha';" | docker compose exec -T db psql -U postgres -v senha="$IDIOMANDO_DB_SENHA"
docker compose restart app
```

---

## 9. Nginx e HTTPS

O certbot com o plugin do Nginx edita o arquivo do site. Para o arquivo final ser igual ao do repositório, o certificado sai em duas fases: primeiro um bloco mínimo, depois a troca pelo arquivo versionado.

**9.1 Instalar e preparar** `[VPS]`. O `export` vale só nesta sessão do SSH: se sair e entrar de novo, repita a linha.
```bash
export DOMINIO=seu.dominio.com.br
sudo apt install -y nginx
sudo rm -f /etc/nginx/sites-enabled/default
```

**9.2 Fase 1, bloco mínimo na porta 80:**
```bash
sudo tee /etc/nginx/conf.d/idiomando.conf <<'EOF'
server {
    listen 80;
    listen [::]:80;
    server_name DOMINIO www.DOMINIO;
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
    }
}
EOF
sudo sed -i "s/DOMINIO/$DOMINIO/g" /etc/nginx/conf.d/idiomando.conf
sudo nginx -t && sudo systemctl reload nginx
```
Se o `nginx -t` reclamar de `socket() [::]:80 failed`, a VPS não tem IPv6: apague as linhas com `listen [::]` (`sudo sed -i '/listen \[::\]/d' /etc/nginx/conf.d/idiomando.conf`) e não crie o registro AAAA.

**Confira** `[PC]`: `curl.exe -i http://DOMINIO/actuator/health` devolve `{"status":"UP"}`.

**9.3 Certificado.** Sem e-mail (o Let's Encrypt também parou de mandar aviso de vencimento; a seção 16 cobre a conferência):
```bash
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/local/bin/certbot
sudo certbot --nginx --register-unsafely-without-email --agree-tos --redirect -d $DOMINIO -d www.$DOMINIO
sudo ls /etc/letsencrypt/live/$DOMINIO /etc/letsencrypt/options-ssl-nginx.conf /etc/letsencrypt/ssl-dhparams.pem
```
**Confira:** os três caminhos existem. Se a pasta se chamar `DOMINIO-0001`, é porque já houve um certificado antes: ajuste `ssl_certificate` e `ssl_certificate_key` no passo seguinte.

**9.4 Fase 2, o arquivo versionado.** `[PC]`:
```powershell
scp infra\nginx\idiomando.conf USUARIO@IP_DA_VPS:/tmp/
```
`[VPS]`:
```bash
sudo install -m 644 /tmp/idiomando.conf /etc/nginx/conf.d/idiomando.conf
sudo sed -i -e 's/\r$//' -e "s/DOMINIO/$DOMINIO/g" /etc/nginx/conf.d/idiomando.conf
sudo nginx -t && sudo systemctl reload nginx
rm /tmp/idiomando.conf
sudo certbot renew --dry-run
```
**Confira:** `nginx -t` ok (se reclamar de `[::]`, mesma regra do 9.2) e `Congratulations, all simulated renewals succeeded`. A renovação roda sozinha por um timer do snap (`systemctl list-timers | grep certbot`).

**Confira** `[PC]`:
```powershell
curl.exe -sI http://DOMINIO/             # 301 para https://DOMINIO/
curl.exe -sI https://www.DOMINIO/        # 301 para https://DOMINIO/
curl.exe -sI https://DOMINIO/            # 200, com strict-transport-security
```
No navegador: `https://DOMINIO` abre com cadeado e a busca funciona.

---

## 10. CSP e cabeçalhos

O arquivo sobe com `Content-Security-Policy-Report-Only`: o navegador só **registra** violações no console, sem bloquear.

1. Abra `https://DOMINIO` no Chrome ou Edge com o console aberto (F12, aba Console).
2. Faça a sessão completa: buscar uma frase, trocar de resultado, tocar o vídeo, ligar o loop, apertar **Falar** (aceite o microfone), usar a tradução, copiar o link.
3. Procure no console mensagens `[Report Only] Refused to ...`. Sem nenhuma, passe para o modo que bloqueia:
```bash
sudo sed -i 's/Content-Security-Policy-Report-Only/Content-Security-Policy/' /etc/nginx/conf.d/idiomando.conf
sudo nginx -t && sudo systemctl reload nginx
```
4. Repita a sessão completa. Se algo quebrar, volte o nome para `-Report-Only` e anote a violação.

**Confira** `[PC]`:
```powershell
curl.exe -s -o NUL -w "%{http_code}`n" https://DOMINIO/actuator/env       # 404
curl.exe -s https://DOMINIO/actuator/health                               # {"status":"UP"}
```
- O vídeo toca, sem o erro 153 do YouTube.
- Microfone e tradução funcionam no domínio (precisam de HTTPS).
- **MDN HTTP Observatory** (o antigo Mozilla Observatory): https://developer.mozilla.org/en-US/observatory, nota **A** ou melhor.

**Uma semana depois**, sem problema, suba o HSTS para um ano (não use `preload`):
```bash
sudo sed -i 's/max-age=300/max-age=31536000/' /etc/nginx/conf.d/idiomando.conf
sudo nginx -t && sudo systemctl reload nginx
```

---

## 11. Rate limit e portas

**11.1 Rajada** `[PC]`. Roda 200 buscas seguidas; **enquanto ele roda**, faça uma busca no site:
```powershell
1..200 | % { curl.exe -s -o NUL -w "%{http_code} " "https://DOMINIO/api/busca?frase=hello" }
```
**Confira:** as primeiras ~11 respostas são `200`, as seguintes `429`, e a busca feita no site durante o laço mostra a mensagem "Muitas buscas seguidas. Espere alguns segundos e tente de novo." no lugar dos resultados. Cinco segundos depois do fim, a busca volta a funcionar. Uso normal (uma busca a cada poucos segundos, cliques em exemplos) nunca recebe 429.

**11.2 IP no log** `[VPS]`: `sudo tail -3 /var/log/nginx/idiomando.access.log` mostra o **seu IP público**, não `127.0.0.1`.

**11.3 Portas** `[PC]`:
```powershell
Test-NetConnection DOMINIO -Port 443    # TcpTestSucceeded : True
Test-NetConnection DOMINIO -Port 5432   # False
Test-NetConnection DOMINIO -Port 8080   # False
```

> **Os números** (1 busca/s por IP com rajada de 10; 5 legendas/s com rajada de 20; teto global de 10 buscas/s) são ponto de partida. Atrás de CGNAT (comum em celular no Brasil) vários usuários dividem um IP, por isso as rajadas são folgadas. Para mudar, edite `rate=` e `burst=` em `/etc/nginx/conf.d/idiomando.conf` e rode `sudo nginx -t && sudo systemctl reload nginx`. Guarde a mudança também em `infra/nginx/idiomando.conf`.

O site está no ar. Daqui para baixo, é a rotina.

---

## 12. Ler os logs

O pacote do Nginx já gira o log por dia e guarda 14 cópias; é o prazo declarado em `privacidade.html`. **Confira** `[VPS]`:
```bash
grep -E "daily|rotate" /etc/logrotate.d/nginx
```
Devolve `daily` e `rotate 14`.

**Relatório com o GoAccess** (requisições por rota, status e tempo, nos 14 dias):
```bash
sudo apt install -y goaccess
sudo zcat -f /var/log/nginx/idiomando.access.log* | goaccess - \
  --log-format='%h - [%d:%t %^] "%r" %s %b "%R" "%u" rt=%T urt=%^' \
  --date-format=%d/%b/%Y --time-format=%T -o /tmp/relatorio.html
```
Traga e abra no navegador `[PC]`:
```powershell
scp USUARIO@IP_DA_VPS:/tmp/relatorio.html .
```
**Confira:** `Total Requests` sem `Failed Requests` (se aparecerem, o `log_format` do Nginx mudou e esta string precisa acompanhar); painéis de **Requested Files** (rota, com as colunas de tempo `Avg. T.S.`, `Cum. T.S.` e `Max. T.S.`) e **HTTP Status Codes**. Os 429 aparecem no painel de status.

**Tempo de resposta da busca (mediana e p95).** O GoAccess não mostra percentis; este comando conta só as buscas que deram 200:
```bash
sudo zcat -f /var/log/nginx/idiomando.access.log* | grep '"GET /api/busca[^"]*" 200 ' | grep -o ' rt=[0-9.]*' | cut -d= -f2 | sort -n \
  | awk '{a[NR]=$1} END{ if (NR) print "buscas=" NR, "p50=" a[int(NR*0.5)+1] "s", "p95=" a[int(NR*0.95)+1] "s", "max=" a[NR] "s"; else print "sem dados" }'
```

---

## 13. Publicar uma versão nova

1. No GitHub, os PRs da mudança entram na `develop`; depois um PR `develop` -> `main` (título `release: vX.Y.Z`).
2. Na `main` atualizada, crie e envie a tag (no IntelliJ: Git -> New Tag `vX.Y.Z`, depois Push com "Push tags").
3. Abra Actions: o job `build` e depois `imagem` ficam verdes. Tag com teste quebrado **não** gera imagem. O pacote `ghcr.io/lucasmanoel1/idiomando:X.Y.Z` aparece em Packages.
4. Na VPS `[VPS]`:
```bash
cd /opt/idiomando
sed -i 's/^VERSAO=.*/VERSAO=X.Y.Z/' .env
docker compose pull app
docker compose up -d app
```
**Confira** (o site fica fora do ar por alguns segundos, uma instância só):
```bash
curl -s 127.0.0.1:8080/actuator/health
curl -s "https://DOMINIO/api/busca?frase=hello" | head -c 200
```
`UP` e um JSON com `resultados`.

---

## 14. Voltar uma versão

```bash
cd /opt/idiomando
sed -i 's/^VERSAO=.*/VERSAO=VERSAO_ANTERIOR/' .env
docker compose up -d app
```
Se a imagem anterior já foi removida da VPS (seção 16), rode `docker compose pull app` antes do `up`. O app de produção nunca muda o schema (Flyway desligado), então versão de app e versão de banco não dependem uma da outra.

**Confira:** o mesmo `health` e a mesma busca da seção 13.

---

## 15. Atualizar o índice e voltar atrás

**Atualizar.** Depois de ingerir no seu computador:
1. `[PC]` Dump, com a contagem para comparar (comandos do passo 7.3).
2. `[PC]` `scp .\idiomando-AAAA-MM-DD.dump USUARIO@IP_DA_VPS:/opt/idiomando/`
3. `[VPS]`
```bash
cd /opt/idiomando
df -h /          # precisa caber mais uma copia do indice: o script mantem o banco atual, o novo e o antigo
./scripts/atualizar-indice.sh idiomando-AAAA-MM-DD.dump
rm idiomando-AAAA-MM-DD.dump
```
O script restaura num banco novo, aplica as permissões, roda `ANALYZE` e mostra as contagens do atual e do novo. Só responda `s` se o **novo** bater com o que você anotou no passo 1. Responder qualquer outra coisa cancela e deixa o banco atual no ar. Ao trocar, o site fica fora do ar por alguns segundos; o banco anterior vira `idiomando_antigo`.

**Confira:** o script termina com `App no ar` e a lista de idiomas; faça uma busca no site.

**Voltar o índice:**
```bash
./scripts/voltar-indice.sh
```
Troca `idiomando` e `idiomando_antigo`. Rodar de novo desfaz a volta. Não existindo `idiomando_antigo`, o script avisa e não faz nada. O antigo só é apagado na **próxima** atualização (o script faz isso sozinho).

---

## 16. Todo mês

`[VPS]`:
```bash
df -h /                                   # disco: avise-se acima de 70%
docker system df                          # imagens, volumes e logs do Docker
docker image prune -a --filter "until=720h"   # remove imagens sem uso criadas ha mais de 30 dias (a que esta em uso nunca sai)
sudo certbot certificates                 # "VALID: NN days" (o aviso por e-mail nao existe mais)
sudo certbot renew --dry-run              # a renovacao automatica ainda funciona
[ -f /var/run/reboot-required ] && echo "REINICIE"
```
- Se aparecer `REINICIE` (atualização de kernel): `sudo reboot`, espere um minuto e abra o site. `restart: unless-stopped` sobe o banco e o app; o Nginx sobe sozinho.
- No painel da Hostinger, olhe os gráficos de CPU, RAM e disco do mês.
- Abra o site e faça uma busca: sem monitor externo, é assim que você descobre uma queda.

**Sinais para subir de plano:** CPU acima de 70% de forma sustentada nos gráficos da Hostinger, ou p95 da busca acima de 1 s (comando do fim da seção 12).

---

## 17. Plano B: Cloudflare, se vier um ataque distribuído

O limite por IP não segura muitos IPs diferentes. A Cloudflare absorve o tráfego antes da VPS, mas tem custo: ela passa a ver todo o tráfego, e é preciso fechar 80/443 para quem não é ela (senão o atacante vai direto na VPS).

1. Crie a conta na Cloudflare, adicione `DOMINIO` e troque os servidores de nomes no Registro.br pelos que ela indicar. Nos registros `A` de `@` e `www`, deixe o proxy ligado (nuvem laranja). Em SSL/TLS, modo **Full (strict)**.
2. **IP real.** Sem isso, todos os pedidos chegam com IP da Cloudflare e o limite por IP vira um limite para todo mundo junto. `[VPS]`:
```bash
{ for f in ips-v4 ips-v6; do curl -fsS https://www.cloudflare.com/$f; echo; done | grep . | sed 's/^/set_real_ip_from /; s/$/;/'; echo 'real_ip_header CF-Connecting-IP;'; } | sudo tee /etc/nginx/conf.d/cloudflare-realip.conf
sudo nginx -t && sudo systemctl reload nginx
```
**Confira:** `sudo tail -3 /var/log/nginx/idiomando.access.log` mostra IPs de visitantes, não os da Cloudflare.
3. **Firewall:** 80 e 443 só para as faixas da Cloudflare.
```bash
for ip in $(curl -fsS https://www.cloudflare.com/ips-v4; echo; curl -fsS https://www.cloudflare.com/ips-v6); do sudo ufw allow from $ip to any port 80,443 proto tcp; done
sudo ufw delete allow 80/tcp
sudo ufw delete allow 443/tcp
sudo ufw status numbered
```
Faça o mesmo no firewall do painel da Hostinger. A Cloudflare muda as faixas de vez em quando: refaça os passos 2 e 3 de tempos em tempos.
4. Atualize `privacidade.html`: a Cloudflare passa a tratar os acessos do site, e a política precisa dizer isso.
5. Para desfazer: nuvem cinza nos registros, apague `cloudflare-realip.conf`, `sudo ufw allow 80/tcp`, `sudo ufw allow 443/tcp` e remova as regras `from` (`sudo ufw status numbered` e `sudo ufw delete N`).

---

## 18. Remover um vídeo do índice

Quando alguém pedir ou um vídeo for removido do YouTube. Sem canal de contato público na versão atual, isso é por iniciativa sua.

1. `[PC]` Remova do banco **local** (substitua `YOUTUBE_ID`). O `video_descartado` impede que a ingestão traga o vídeo de volta; as tabelas filhas saem primeiro por causa das chaves estrangeiras:
```powershell
docker compose exec db psql -U postgres -d idiomando -v ON_ERROR_STOP=1 -c "BEGIN;" -c "DELETE FROM ocorrencia WHERE video_id = (SELECT id FROM video WHERE youtube_id = 'YOUTUBE_ID');" -c "DELETE FROM trecho WHERE video_id = (SELECT id FROM video WHERE youtube_id = 'YOUTUBE_ID');" -c "DELETE FROM video WHERE youtube_id = 'YOUTUBE_ID';" -c "INSERT INTO video_descartado (youtube_id, motivo) VALUES ('YOUTUBE_ID', 'REMOVIDO_MANUAL') ON CONFLICT DO NOTHING;" -c "COMMIT;"
```
**Confira:** `DELETE` com contagens maiores que zero nas três primeiras linhas e `INSERT 0 1`.
2. Se o vídeo estiver num CSV de `data/`, comente a linha com `#` (a lista continua igual para quem refizer a carga).
3. `[PC]` + `[VPS]` Leve para produção com a seção 15. Até lá, o site pula o vídeo sozinho se o YouTube já o recusar (erro 100, 101 ou 150).
