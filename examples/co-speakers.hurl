# Co-speakers: our own examples (workflow/specs/co-speakers.md), happy path and the decided edge cases.
# Run: hurl --test --variable base_url=http://localhost:8080 co-speakers.hurl

# Setup: main speaker, three others, one talk
POST {{base_url}}/speakers
{ "name": "Main", "email": "main-{{newUuid}}@example.com" }
HTTP 201
[Captures]
main_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Co A", "email": "co-a-{{newUuid}}@example.com" }
HTTP 201
[Captures]
a_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Co B", "email": "co-b-{{newUuid}}@example.com" }
HTTP 201
[Captures]
b_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Co C", "email": "co-c-{{newUuid}}@example.com" }
HTTP 201
[Captures]
c_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Pairing on stage", "format": "PRESENTATION", "level": "BEGINNER", "speakerId": {{main_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"
[Asserts]
jsonpath "$.coSpeakerIds" count == 0

# Add B, then A: order of adding
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{b_id}}
HTTP 200
[Asserts]
jsonpath "$.id" == {{talk_id}}
jsonpath "$.speakerId" == {{main_id}}
jsonpath "$.coSpeakerIds" count == 1
jsonpath "$.coSpeakerIds[0]" == {{b_id}}

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{a_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 2
jsonpath "$.coSpeakerIds[0]" == {{b_id}}
jsonpath "$.coSpeakerIds[1]" == {{a_id}}

# Adding again changes nothing
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{b_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 2
jsonpath "$.coSpeakerIds[0]" == {{b_id}}

# A third co-speaker: 409
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{c_id}}
HTTP 409

# The main speaker as co-speaker: 400
PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{main_id}}
HTTP 400

# Unknown talk / speaker: 404
PUT {{base_url}}/talks/999999999/co-speakers/{{a_id}}
HTTP 404

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/999999999
HTTP 404

# Shown on GET
GET {{base_url}}/talks/{{talk_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds[0]" == {{b_id}}
jsonpath "$.coSpeakerIds[1]" == {{a_id}}

# A co-speaker's own talk list doesn't include it
GET {{base_url}}/speakers/{{a_id}}/talks
HTTP 200
[Asserts]
jsonpath "$" count == 0

# Remove B; removing again is 404; re-adding goes last
DELETE {{base_url}}/talks/{{talk_id}}/co-speakers/{{b_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds" count == 1
jsonpath "$.coSpeakerIds[0]" == {{a_id}}

DELETE {{base_url}}/talks/{{talk_id}}/co-speakers/{{b_id}}
HTTP 404

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{b_id}}
HTTP 200
[Asserts]
jsonpath "$.coSpeakerIds[0]" == {{a_id}}
jsonpath "$.coSpeakerIds[1]" == {{b_id}}

DELETE {{base_url}}/talks/999999999/co-speakers/{{a_id}}
HTTP 404
