-- Opt-in Flyway location. Run as the RAG schema owner with extension creation privileges.
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE TABLE investory_rag.chunks (
    collection text NOT NULL,
    id text NOT NULL,
    source text NOT NULL,
    chunk_index integer NOT NULL CHECK (chunk_index >= 0),
    content text NOT NULL,
    embedding public.vector NOT NULL CHECK (public.vector_norm(embedding) > 0),
    PRIMARY KEY (collection, id)
);
