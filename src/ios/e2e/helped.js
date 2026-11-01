// Another driver says they parked in the spot the test driver reported, so the app has
// someone to thank for. Runs inside Maestro, against the local backend.
const api = 'http://localhost:8000'
const jsonHeaders = { 'Content-Type': 'application/json' }

const login = http.post(api + '/auth/dev-login', {
    headers: jsonHeaders,
    body: JSON.stringify({ auth_provider: 'dev', external_id: 'e2e-helper-' + Date.now(), display_name: 'Helper' }),
})
const token = json(login.body).access_token
const auth = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' }

const near = 'lat=32.0853&lng=34.7818&radius_m=800&me_lat=32.0853&me_lng=34.7818'
const spots = json(http.get(api + '/spots?' + near, { headers: auth }).body)
const spot = spots.filter(function (s) { return s.note === 'Low curb e2e' })[0]
if (!spot) throw new Error('the reported spot is not on the map')

const done = http.post(api + '/spots/' + spot.id + '/feedback', {
    headers: auth,
    body: JSON.stringify({ type: 'confirmed_taken' }),
})
if (!done.ok) throw new Error('feedback failed: ' + done.status)
