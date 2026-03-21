CREATE TABLE IF NOT EXISTS notification_events (

                                                   id UUID PRIMARY KEY,
                                                   user_id UUID NOT NULL,
                                                   type TEXT NOT NULL,
                                                   message TEXT,
                                                   sent BOOLEAN DEFAULT FALSE,
                                                   created_at TIMESTAMPTZ DEFAULT NOW()

    );