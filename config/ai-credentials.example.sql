-- Copy this file to config/ai-credentials.sql and fill in your own values.
-- Leave unused rows unchanged; a REPLACE_ME value keeps that row disabled.
SET NAMES utf8mb4;

SET @spark_key = 'REPLACE_ME';
SET @spark_secret = 'REPLACE_ME';
UPDATE ai_properties
SET api_key = @spark_key, api_secret = @spark_secret, is_enabled = 1
WHERE id = 1 AND @spark_key <> 'REPLACE_ME' AND @spark_secret <> 'REPLACE_ME';

SET @deepseek_key = 'REPLACE_ME';
UPDATE ai_properties
SET api_key = @deepseek_key, is_enabled = 1
WHERE id IN (3, 4) AND @deepseek_key <> 'REPLACE_ME';

SET @agent_key = 'REPLACE_ME';
SET @agent_secret = 'REPLACE_ME';
-- These rows assume the workflows share one bound application; use separate keys per row if they do not.
SET @agent_flow_8 = 'REPLACE_ME';
SET @agent_flow_9 = 'REPLACE_ME';
SET @agent_flow_11 = 'REPLACE_ME';
SET @agent_flow_12 = 'REPLACE_ME';
SET @agent_flow_17 = 'REPLACE_ME';
SET @agent_flow_18 = 'REPLACE_ME';

UPDATE agent_properties
SET api_key = @agent_key, api_secret = @agent_secret, api_flow_id = @agent_flow_8
WHERE id = 8 AND @agent_key <> 'REPLACE_ME' AND @agent_secret <> 'REPLACE_ME' AND @agent_flow_8 <> 'REPLACE_ME';
UPDATE agent_properties
SET api_key = @agent_key, api_secret = @agent_secret, api_flow_id = @agent_flow_9
WHERE id = 9 AND @agent_key <> 'REPLACE_ME' AND @agent_secret <> 'REPLACE_ME' AND @agent_flow_9 <> 'REPLACE_ME';
UPDATE agent_properties
SET api_key = @agent_key, api_secret = @agent_secret, api_flow_id = @agent_flow_11
WHERE id = 11 AND @agent_key <> 'REPLACE_ME' AND @agent_secret <> 'REPLACE_ME' AND @agent_flow_11 <> 'REPLACE_ME';
UPDATE agent_properties
SET api_key = @agent_key, api_secret = @agent_secret, api_flow_id = @agent_flow_12
WHERE id = 12 AND @agent_key <> 'REPLACE_ME' AND @agent_secret <> 'REPLACE_ME' AND @agent_flow_12 <> 'REPLACE_ME';
UPDATE agent_properties
SET api_key = @agent_key, api_secret = @agent_secret, api_flow_id = @agent_flow_17
WHERE id = 17 AND @agent_key <> 'REPLACE_ME' AND @agent_secret <> 'REPLACE_ME' AND @agent_flow_17 <> 'REPLACE_ME';
UPDATE agent_properties
SET api_key = @agent_key, api_secret = @agent_secret, api_flow_id = @agent_flow_18
WHERE id = 18 AND @agent_key <> 'REPLACE_ME' AND @agent_secret <> 'REPLACE_ME' AND @agent_flow_18 <> 'REPLACE_ME';
