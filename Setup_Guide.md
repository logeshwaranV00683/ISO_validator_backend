# 1.  Start the Infra:

Since MySQL is already running locally on 3306, the compose file only starts Redis + RabbitMQ:
```# From inside iso-validator/
docker-compose up -d

# Check both containers are healthy
docker ps
# Should show: iso-validator-redis and iso-validator-rabbitmq both "Up (healthy)" 
```

If not in the local, you can use this command in your docker for the instalation 
```
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:latest
```

# 2. Load the Schema into Your Local MySQL:
Since MySQL is local (not in Docker), run schema manually:
```
mysql -u root -p iso_validator_db < schema.sql
mysql -u root -p iso_validator_db < seed.sql
```
Or if the database doesn't exist yet:

```
mysql -u root -p -e "CREATE DATABASE iso_validator_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p iso_validator_db < schema.sql
mysql -u root -p iso_validator_db < seed.sql
```
# 3. Verify (run these in MySQL):

```
USE iso_validator_db;

SHOW TABLES;                        -- must show 16 tables
SELECT COUNT(*) FROM ollama_config; -- must return 9
SELECT COUNT(*) FROM system_config; -- must return 6
DESCRIBE validation_runs;           -- check run_reference column
SHOW INDEX FROM validation_runs;    -- check idx_runs_reference exists
```

# 4. Test Redis & RabbitMQ:

```
# Redis ping
docker exec iso-validator-redis redis-cli ping
# Expected: PONG

# RabbitMQ management UI
# Open browser → http://localhost:15672
# Login: guest / guest
```

# 5. Ollama:

Ollama accepts connections from other containers and services on the Docker network

Pull a model:

The container starts empty. You must download a model:

```
docker exec -it iso-ollama ollama pull mistral

or

docker exec -it iso-ollama ollama pull llama3.2

```

The model files are stored in your ollama_data volume, so they persist across restarts.

Verify:

curl http://localhost:11434/api/tags   You should see your downloaded models.

