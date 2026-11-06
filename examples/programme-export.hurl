# Programme export: our own examples (workflow/specs/programme-export.md).
# Run: hurl --test --variable base_url=http://localhost:8080 programme-export.hurl
# The programme is shared, so this only checks our own rows and the format, not the whole export.
# Uses R2/S2 and empties it again at the end.

# Empty the cells we use first: the programme is shared, and a failed earlier run (or, in production, a harness
# check) can leave a talk there. Against production this can disturb a harness check that is using the cell.
DELETE {{base_url}}/programme/R2/S2
HTTP *


POST {{base_url}}/speakers
{ "name": "Grace Export", "email": "export-g-{{newUuid}}@example.com" }
HTTP 201
[Captures]
grace_id: jsonpath "$.id"

POST {{base_url}}/speakers
{ "name": "Ada Export", "email": "export-a-{{newUuid}}@example.com" }
HTTP 201
[Captures]
ada_id: jsonpath "$.id"

POST {{base_url}}/talks
{ "title": "Kotlin, \"really\" fast", "format": "LIGHTNING_20", "level": "BEGINNER", "speakerId": {{grace_id}} }
HTTP 201
[Captures]
talk_id: jsonpath "$.id"

PUT {{base_url}}/talks/{{talk_id}}/co-speakers/{{ada_id}}
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/401
{ "score": 5 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/ratings/402
{ "score": 4 }
HTTP 200

PUT {{base_url}}/talks/{{talk_id}}/status
{ "status": "ACCEPTED" }
HTTP 200

PUT {{base_url}}/programme/R2/S2
{ "talkId": {{talk_id}} }
HTTP 200

# JSON: our row, speakers main first
GET {{base_url}}/programme
HTTP 200
[Asserts]
jsonpath "$[?(@.talkId == {{talk_id}})].slot" == "S2"
jsonpath "$[?(@.talkId == {{talk_id}})].start" == "10:15"
jsonpath "$[?(@.talkId == {{talk_id}})].end" == "11:15"
jsonpath "$[?(@.talkId == {{talk_id}})].room" == "R2"
jsonpath "$[?(@.talkId == {{talk_id}})].title" == "Kotlin, \"really\" fast"
jsonpath "$[?(@.talkId == {{talk_id}})].format" == "LIGHTNING_20"
jsonpath "$[?(@.talkId == {{talk_id}})].speakers[0]" == "Grace Export"
jsonpath "$[?(@.talkId == {{talk_id}})].speakers[1]" == "Ada Export"

# CSV: header, our row quoted only where needed, text/csv even when asking for JSON
GET {{base_url}}/programme.csv
Accept: application/json
HTTP 200
[Asserts]
header "Content-Type" startsWith "text/csv"
body startsWith "slot,start,end,room,talkId,title,format,speakers\n"
body contains "S2,10:15,11:15,R2,{{talk_id}},\"Kotlin, \"\"really\"\" fast\",LIGHTNING_20,Grace Export; Ada Export\n"
body endsWith "\n"
body not endsWith "\n\n"

DELETE {{base_url}}/programme/R2/S2
HTTP 204
