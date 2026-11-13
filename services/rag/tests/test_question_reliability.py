import pytest

from investory_rag.reliability import (
    has_textual_support, unsupported_document_question, unsupported_facts_question
)


@pytest.mark.parametrize("question", [
    "What is the current value of my brokerage account?",
    "How much did my portfolio gain today?",
    "What is the live price of NVIDIA right now?",
    "What is my current bank account balance?",
    "What is my current net worth?",
    "What is my current portfolio value?",
    "What is it?",
    "Which option is best?",
    "Should I buy NVDA?",
    "tell me more",
])
def test_unsupported_document_questions(question):
    assert unsupported_document_question(question)


@pytest.mark.parametrize("question", [
    "What is the fixed withdrawal order in Reserve + Harvest?",
    "How are internal transfers treated in portfolio accounting?",
    "How are the Past, Current and Future years handled in planning?",
])
def test_positive_document_questions(question):
    assert not unsupported_document_question(question)


def test_facts_are_limited_to_authoritative_java_aggregates():
    assert not unsupported_facts_question("What is my current net worth?")
    assert not unsupported_facts_question("What is my current portfolio value?")
    assert not unsupported_facts_question("Explain my liquid assets")
    assert unsupported_facts_question("How much did my portfolio gain today?")
    assert unsupported_facts_question("What was my trading profit?")
    assert unsupported_facts_question("What is my brokerage balance?")


def test_high_vector_score_without_relevant_text_is_not_evidence():
    assert not has_textual_support("reserve withdrawal strategy", [
        {"text": "Cats and dogs and bicycles"}
    ])
    assert has_textual_support("reserve withdrawal strategy", [
        {"text": "The reserve is funded before withdrawal"}
    ])
