CREATE TABLE video_descartado (
    youtube_id VARCHAR(20) PRIMARY KEY,
    motivo     VARCHAR(30) NOT NULL,
    data       TIMESTAMP   NOT NULL DEFAULT now()
);
