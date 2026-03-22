CREATE TABLE IF NOT EXISTS audit_logs (

                                          id UUID PRIMARY KEY,
                                          user_id UUID,
                                          action TEXT,
                                          entity_type TEXT,
                                          entity_id TEXT,
                                          details TEXT,
                                          created_at TIMESTAMPTZ DEFAULT NOW()

    );