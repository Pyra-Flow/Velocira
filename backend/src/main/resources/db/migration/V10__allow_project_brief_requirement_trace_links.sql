ALTER TABLE requirement_trace_links
    DROP CONSTRAINT ck_requirement_trace_link_type;

ALTER TABLE requirement_trace_links
    ADD CONSTRAINT ck_requirement_trace_link_type
    CHECK (link_type IN ('EVIDENCE', 'ASSUMPTION', 'CONTROL', 'PROJECT_BRIEF'));
