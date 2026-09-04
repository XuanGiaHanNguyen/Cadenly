-- Demo accounts, same three people UserDirectoryService hardcoded before
-- this migration - kept as real, loggable-in accounts so the dashboard has
-- someone to sign in as out of the box. Password for all three is
-- "password123" (BCrypt-hashed below); this is local demo data, not a
-- production credential.
INSERT INTO users (id, email, display_name, password_hash) VALUES
    ('11111111-1111-1111-1111-111111111111', 'sarah@cadenly.local', 'Sarah Kim', '$2a$10$tk2vRjYUdRn8.xMwmqDmSusjchaEt7xw7jx.EXqGscO4jhuHIOeZO'),
    ('22222222-2222-2222-2222-222222222222', 'john@cadenly.local',  'John',      '$2a$10$tk2vRjYUdRn8.xMwmqDmSusjchaEt7xw7jx.EXqGscO4jhuHIOeZO'),
    ('33333333-3333-3333-3333-333333333333', 'priya@cadenly.local', 'Priya',     '$2a$10$tk2vRjYUdRn8.xMwmqDmSusjchaEt7xw7jx.EXqGscO4jhuHIOeZO');

-- Sarah keeps her two name variants; John and Priya each get their one alias.
INSERT INTO user_name_aliases (user_id, alias) VALUES
    ('11111111-1111-1111-1111-111111111111', 'sarah kim'),
    ('11111111-1111-1111-1111-111111111111', 'sarah'),
    ('22222222-2222-2222-2222-222222222222', 'john'),
    ('33333333-3333-3333-3333-333333333333', 'priya');
