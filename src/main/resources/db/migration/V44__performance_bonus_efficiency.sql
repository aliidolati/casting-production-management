-- Allow audit snapshots to store the full score-to-base ratio.
-- Widening preserves existing efficiencies and can be replayed after a schema update.
ALTER TABLE performance_events
    MODIFY COLUMN old_efficiency DECIMAL(16,4) NULL,
    MODIFY COLUMN new_efficiency DECIMAL(16,4) NULL;
