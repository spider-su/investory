import json

import pytest
from langchain_core.messages import AIMessage
from langchain_core.runnables import RunnableLambda

from investory_rag.service import route_question


def fake_model(payload):
    return RunnableLambda(lambda _: AIMessage(content=json.dumps(payload)))


def test_valid_semantic_plan_is_read_only_and_bounded():
    result = route_question(fake_model({"tools": ["HOLDINGS", "RETIREMENT"], "symbol": None}),
                            "Which of my investments support retirement?")
    assert result == {"tools": ["HOLDINGS", "RETIREMENT"], "symbol": None}


@pytest.mark.parametrize("payload", [
    {"tools": ["DELETE_PORTFOLIO"], "symbol": None},
    {"tools": ["HOLDINGS", "HOLDINGS"], "symbol": None},
    {"tools": ["MARKET"], "symbol": "../../private"},
    {"tools": ["MARKET"], "symbol": 100},
    {"tools": "HOLDINGS", "symbol": None},
    {"tools": ["HOLDINGS"], "symbol": None, "privateSnapshot": "123"},
])
def test_rejects_invalid_or_unsafe_model_plans(payload):
    with pytest.raises(ValueError):
        route_question(fake_model(payload), "ambiguous request")


def test_unrelated_or_unsupported_question_can_abstain():
    assert route_question(fake_model({"tools": [], "symbol": None}),
                          "Predict my portfolio price tomorrow") == {
                              "tools": [], "symbol": None}


def test_cross_domain_investory_mcp_routes_are_bounded():
    result = route_question(
        fake_model({"tools": ["GENERAL_PROFILE", "LONG_TERM", "SIMULATION"],
                    "symbol": None}),
        "Summarize my whole wealth, rental assets and retirement simulation")
    assert result == {
        "tools": ["GENERAL_PROFILE", "LONG_TERM", "SIMULATION"],
        "symbol": None,
    }
