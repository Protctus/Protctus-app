-- Protctus Lab: Microlearning & Quiz System Schema
CREATE TABLE IF NOT EXISTS lab_chapters (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS lab_topics (
    id TEXT PRIMARY KEY,
    chapter_id TEXT NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (chapter_id) REFERENCES lab_chapters(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS lab_quizzes (
    id TEXT PRIMARY KEY,
    topic_id TEXT NOT NULL,
    question TEXT NOT NULL,
    options TEXT NOT NULL, -- JSON formatted array
    correct_answer_index INTEGER NOT NULL,
    explanation TEXT NOT NULL,
    FOREIGN KEY (topic_id) REFERENCES lab_topics(id) ON DELETE CASCADE
);
