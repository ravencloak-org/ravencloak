-- =========================
-- Setup UUIDv7 Function
-- =========================
-- UUIDv7 is a time-ordered UUID that includes a timestamp component
-- for better database performance and natural ordering
-- Spec: https://datatracker.ietf.org/doc/html/draft-peabody-dispatch-new-uuid-format

CREATE OR REPLACE FUNCTION uuidv7() RETURNS UUID AS $$
DECLARE
    -- Unix timestamp in milliseconds (48 bits)
    unix_ts_ms BIGINT;
    -- Random bytes for uniqueness
    rand_a BYTEA;
    rand_b BYTEA;
    -- Final UUID bytes
    uuid_bytes BYTEA;
BEGIN
    -- Get current Unix timestamp in milliseconds
    unix_ts_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;

    -- Generate random bytes
    -- 2 bytes for rand_a (12 bits will be used after version bits)
    rand_a := gen_random_bytes(2);
    -- 8 bytes for rand_b (62 bits will be used after variant bits)
    rand_b := gen_random_bytes(8);

    -- Construct UUID bytes:
    -- 6 bytes: timestamp (48 bits)
    -- 2 bytes: version (4 bits) + rand_a (12 bits)
    -- 8 bytes: variant (2 bits) + rand_b (62 bits)
    uuid_bytes :=
        -- Timestamp (48 bits = 6 bytes)
        int8send(unix_ts_ms) ||
        -- Version 7 (0111) + 12 random bits
        set_byte(rand_a, 0, (get_byte(rand_a, 0) & 15) | 112) ||
        set_byte(rand_a, 1, get_byte(rand_a, 1)) ||
        -- Variant 10 (RFC 4122) + 62 random bits
        set_byte(rand_b, 0, (get_byte(rand_b, 0) & 63) | 128) ||
        substring(rand_b from 2);

    -- Convert to UUID format (with dashes)
    RETURN encode(uuid_bytes, 'hex')::UUID;
END;
$$ LANGUAGE plpgsql VOLATILE;

-- Create a comment explaining the function
COMMENT ON FUNCTION uuidv7() IS 'Generate a UUIDv7 (time-ordered UUID with timestamp prefix for better database performance)';

COMMIT;
