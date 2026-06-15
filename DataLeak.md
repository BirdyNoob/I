{
"scenario": {
"title": "Customer Data Summarization",
"domain": "DATA_LEAKAGE",
"difficulty": "BEGINNER",
"description": "Employee wants to use AI to summarize customer feedback."
},
"scoreConfig": {
"maxScore": 100,
"passThreshold": 70,
"criticalThreshold": 50
},
"steps": [
{
"stepType": "INFORMATION",
"sortOrder": 0,
"title": "Customer Dataset",
"contextJson": {
"artifact_type": "document",
"label": "CustomerFeedback.xlsx",
"content": "Customer Name: John Smith, Phone: 9876543210, Email: john@gmail.com"
}
},
{
"stepType": "MCQ",
"sortOrder": 1,
"title": "Identify Sensitive Information",
"content": "Which information should not be uploaded into AI systems?",
"options": [
{
"optionText": "Customer Name",
"isCorrect": true,
"points": 10
},
{
"optionText": "Customer Email",
"isCorrect": true,
"points": 10
},
{
"optionText": "Customer Phone Number",
"isCorrect": true,
"points": 10
},
{
"optionText": "General Feedback Summary",
"isCorrect": false,
"points": 10
}
]
},
{
"stepType": "FREE_TEXT",
"sortOrder": 2,
"title": "Prepare Safe Prompt",
"content": "Explain how you would safely use AI for this task.",
"linkedIntents": [
{
"intentCode": "REDACT_DATA",
"mandatory": true,
"weight": 2
},
{
"intentCode": "ANONYMIZE_DATA",
"mandatory": false,
"weight": 1
},
{
"intentCode": "EXPOSE_PII",
"mandatory": false,
"weight": 2
}
]
},
{
"stepType": "FEEDBACK",
"sortOrder": 3,
"title": "Learning Outcome",
"content": "Remove all personal information before using AI systems."
}
]
}