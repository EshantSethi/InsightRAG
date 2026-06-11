-- Structured release data for the (fictional) Nimbus framework. This is the kind of precise,
-- tabular fact — exact versions, dates, Java requirements — that free-text RAG over prose docs
-- answers unreliably, which is exactly why the agent routes such questions to SQL instead.
DROP TABLE IF EXISTS nimbus_release;

CREATE TABLE nimbus_release (
    component    VARCHAR(40)  NOT NULL,  -- e.g. nimbus-core, nimbus-data, nimbus-cache, nimbus-web
    version      VARCHAR(20)  NOT NULL,  -- semantic version string
    release_date DATE         NOT NULL,
    status       VARCHAR(10)  NOT NULL,  -- stable | lts | beta
    min_java     INT          NOT NULL,  -- minimum required Java major version
    PRIMARY KEY (component, version)
);
