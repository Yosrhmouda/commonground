CREATE TABLE IF NOT EXISTS visitor_sessions (
  token_hash VARCHAR(64) PRIMARY KEY,
  actor_id VARCHAR(36) NOT NULL,
  expires_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS places (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL,
  organizer_id VARCHAR(36),
  title VARCHAR(100) NOT NULL,
  location VARCHAR(180) NOT NULL,
  city VARCHAR(100) NOT NULL,
  country VARCHAR(2) NOT NULL,
  description VARCHAR(1500) NOT NULL,
  stage VARCHAR(20) NOT NULL DEFAULT 'reported',
  organizer_name VARCHAR(100),
  cleanup_date VARCHAR(10),
  cleanup_time VARCHAR(5),
  time_zone VARCHAR(80),
  cleanup_at BIGINT,
  volunteer_target INTEGER,
  supplies VARCHAR(1500),
  result_text VARCHAR(1500),
  live_url VARCHAR(2048),
  live_active BOOLEAN NOT NULL DEFAULT FALSE,
  created_at BIGINT NOT NULL,
  completed_at BIGINT
);
CREATE TABLE IF NOT EXISTS place_media (
  id VARCHAR(36) PRIMARY KEY,
  place_id VARCHAR(36) NOT NULL REFERENCES places(id),
  phase VARCHAR(10) NOT NULL,
  content_type VARCHAR(50) NOT NULL,
  original_name VARCHAR(150) NOT NULL,
  size_bytes BIGINT NOT NULL,
  created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS place_likes (
  place_id VARCHAR(36) NOT NULL REFERENCES places(id),
  actor_id VARCHAR(36) NOT NULL,
  PRIMARY KEY(place_id, actor_id)
);
CREATE TABLE IF NOT EXISTS participants (
  place_id VARCHAR(36) NOT NULL REFERENCES places(id),
  actor_id VARCHAR(36) NOT NULL,
  PRIMARY KEY(place_id, actor_id)
);
CREATE INDEX IF NOT EXISTS idx_places_created ON places(created_at);
CREATE INDEX IF NOT EXISTS idx_media_place ON place_media(place_id);

CREATE TABLE IF NOT EXISTS cleanup_results (
 id VARCHAR(36) PRIMARY KEY,
 place_id VARCHAR(36) NOT NULL REFERENCES places(id),
 description VARCHAR(1500) NOT NULL,
 created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS result_media (
 result_id VARCHAR(36) NOT NULL REFERENCES cleanup_results(id),
 media_id VARCHAR(36) NOT NULL REFERENCES place_media(id),
 PRIMARY KEY(result_id, media_id)
);
CREATE INDEX IF NOT EXISTS idx_results_place ON cleanup_results(place_id);
