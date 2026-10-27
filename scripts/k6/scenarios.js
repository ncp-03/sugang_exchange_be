import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';

// LT-07 only: 409 responses whose API error code is EXCHANGE_CONFLICT.
const conflict409 = new Counter('conflict_409');

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const studentId = Number(__ENV.STUDENT_ID || 1);
const profile = (__ENV.PROFILE || 'smoke').toLowerCase();
const duration = __ENV.DURATION || (profile === 'lt16' ? '30m' : '1m');
const browseVus = Number(__ENV.BROWSE_VUS || 5);
const studentVus = Number(__ENV.STUDENT_VUS || 2);
const hotVus = Number(__ENV.HOT_VUS || 5);
const mixedVus = Number(__ENV.MIXED_VUS || 5);
const stepVus = Number(__ENV.STEP_VUS || 20);
const baselineVus = Number(__ENV.BASELINE_VUS || 1);
const spikeVus = Number(__ENV.SPIKE_VUS || 32);
const hotOfferingId = Number(__ENV.HOT_OFFERING_ID || 9);
const searchKeyword = __ENV.SEARCH_KEYWORD || '프로그래밍';
const thinkTime = Number(__ENV.THINK_TIME ?? 1);
const authMode = (__ENV.AUTH_MODE || 'TOKEN').toUpperCase();
const studentNo = __ENV.STUDENT_NO || '2024000001';
const studentPassword = __ENV.STUDENT_PASSWORD || studentNo;
const writeVus = Number(__ENV.WRITE_VUS || 20);
const writeIterations = Number(__ENV.WRITE_ITERATIONS || 100);
const fixtureStart = 100001;
let tokenCache = {};

const profiles = ['smoke', 'load', 'lt01', 'lt02', 'lt03', 'lt04', 'lt05', 'lt06', 'lt07', 'lt08', 'lt09', 'lt11', 'lt12', 'lt16', 'lt17', 'lt18'];
if (!profiles.includes(profile)) {
  throw new Error('PROFILE must be one of: ' + profiles.join(', '));
}
for (const [name, value] of Object.entries({
  STUDENT_ID: studentId, HOT_OFFERING_ID: hotOfferingId, BROWSE_VUS: browseVus,
  STUDENT_VUS: studentVus, HOT_VUS: hotVus, MIXED_VUS: mixedVus,
  STEP_VUS: stepVus, BASELINE_VUS: baselineVus, SPIKE_VUS: spikeVus,
  WRITE_VUS: writeVus, WRITE_ITERATIONS: writeIterations,
})) {
  if (!Number.isInteger(value) || value < 1) {
    throw new Error(name + ' must be a positive integer');
  }
}
if (!Number.isFinite(thinkTime) || thinkTime < 0 || !searchKeyword.trim()) {
  throw new Error('THINK_TIME must be nonnegative and SEARCH_KEYWORD must not be blank');
}
if (profile === 'lt18' && baselineVus >= spikeVus) {
  throw new Error('SPIKE_VUS must be greater than BASELINE_VUS');
}
if (!['TOKEN', 'DEMO'].includes(authMode)) {
  throw new Error('AUTH_MODE must be TOKEN or DEMO');
}
const writeProfiles = ['lt03', 'lt04', 'lt06', 'lt07', 'lt08'];
if (writeProfiles.includes(profile) && __ENV.ALLOW_WRITES !== '1') {
  throw new Error('Write profiles require ALLOW_WRITES=1 on a dedicated load-test database');
}
if (['lt03', 'lt06'].includes(profile) && writeIterations > 5000) {
  throw new Error('WRITE_ITERATIONS must be <= 5000 for these fixture-backed profiles');
}

function constant(vus, exec) {
  return { executor: 'constant-vus', vus, duration, exec };
}

function writeBurst(executorName, iterations = writeIterations) {
  return { executor: 'shared-iterations', vus: writeVus, iterations,
    maxDuration: __ENV.WRITE_MAX_DURATION || '10m', exec: executorName };
}

function loadThresholds() {
  return { checks: ['rate>0.99'], http_req_failed: ['rate<0.01'] };
}

function buildOptions() {
  switch (profile) {
    case 'smoke':
      return {
        scenarios: { api_smoke: { executor: 'shared-iterations', vus: 1, iterations: 1, exec: 'apiSmoke' } },
        thresholds: { checks: ['rate==1'] },
      };
    case 'lt01':
      return { scenarios: { course_list_search: constant(browseVus, 'courseListSearch') }, thresholds: loadThresholds() };
    case 'lt02':
      return { scenarios: { hotspot_detail: constant(hotVus, 'hotspotCourse') }, thresholds: loadThresholds() };
    case 'lt03':
      return { scenarios: { exchange_registration: writeBurst('exchangeRegistration') },
        thresholds: { checks: ['rate>0.99'] } };
    case 'lt04':
      return { scenarios: { same_enrollment_race: writeBurst('sameEnrollmentRace', writeVus) },
        thresholds: { checks: ['rate>0.99'] } };
    case 'lt05':
      return { scenarios: { queue_ranking: constant(studentVus, 'queueRanking') }, thresholds: loadThresholds() };
    case 'lt06':
      return { scenarios: { automatic_matching: writeBurst('automaticMatching') },
        thresholds: { checks: ['rate>0.99'] } };
    case 'lt07':
      return { scenarios: { same_pair_race: writeBurst('samePairRace', writeVus) },
        thresholds: { checks: ['rate>0.99'] } };
    case 'lt08':
      return { scenarios: { simultaneous_accept: writeBurst('simultaneousAccept', 2) },
        thresholds: { checks: ['rate==1'] } };
    case 'lt09':
      return { scenarios: { student_consistency: constant(studentVus, 'studentConsistency') }, thresholds: loadThresholds() };
    case 'lt11':
      return { scenarios: { session_polling: constant(studentVus, 'sessionPolling') }, thresholds: loadThresholds() };
    case 'load':
    case 'lt12':
      return {
        scenarios: {
          course_list_search: constant(browseVus, 'courseListSearch'),
          hotspot_detail: constant(hotVus, 'hotspotCourse'),
          student_consistency: constant(studentVus, 'studentConsistency'),
          session_polling: constant(studentVus, 'sessionPolling'),
        },
        thresholds: loadThresholds(),
      };
    case 'lt16':
      return { scenarios: { read_soak: constant(mixedVus, 'mixedRead') }, thresholds: loadThresholds() };
    case 'lt17':
      return {
        scenarios: {
          read_steps: {
            executor: 'ramping-vus', exec: 'mixedRead', startVUs: 1,
            stages: [
              { duration: __ENV.STEP_DURATION || '1m', target: Math.max(1, Math.ceil(stepVus / 4)) },
              { duration: __ENV.STEP_DURATION || '1m', target: Math.max(1, Math.ceil(stepVus / 2)) },
              { duration: __ENV.STEP_DURATION || '1m', target: stepVus },
              { duration: __ENV.STEP_DURATION || '1m', target: 0 },
            ],
          },
        },
        thresholds: loadThresholds(),
      };
    case 'lt18':
      return {
        scenarios: {
          read_spike: {
            executor: 'ramping-vus', exec: 'mixedRead', startVUs: baselineVus,
            stages: [
              { duration: __ENV.BASELINE_DURATION || '90s', target: baselineVus },
              { duration: '5s', target: spikeVus },
              { duration: __ENV.SPIKE_DURATION || '180s', target: spikeVus },
              { duration: '5s', target: baselineVus },
              { duration: __ENV.RECOVERY_DURATION || '90s', target: baselineVus },
            ],
          },
        },
        thresholds: loadThresholds(),
      };
  }
}

export const options = buildOptions();

function json(response) {
  try {
    return response.json();
  } catch (_) {
    return null;
  }
}

function get(path, headers = {}) {
  const endpoint = path.startsWith('/api/courses/') && path !== '/api/courses/filters'
    ? '/api/courses/{offeringId}' : path.split('?')[0];
  return http.get(`${baseUrl}${path}`, { headers, tags: { endpoint } });
}

function post(path, body, headers = {}) {
  return http.post(`${baseUrl}${path}`, body === null ? null : JSON.stringify(body), {
    headers: { ...headers, 'Content-Type': 'application/json' },
    tags: { endpoint: path.replace(/\d+/g, '{id}') },
  });
}

function syntheticStudentNo(index) {
  return 'LT20261026' + String(index + 1).padStart(5, '0');
}

function authHeaders(id, index = null) {
  if (authMode === 'DEMO') return { 'X-Student-Id': String(id) };
  const key = String(id);
  if (!tokenCache[key]) {
    const no = index === null ? studentNo : syntheticStudentNo(index);
    const password = index === null ? studentPassword : no;
    const response = post('/api/auth/login', { studentNo: no, password });
    const body = json(response);
    if (response.status !== 200 || !body?.accessToken || body.studentId !== id) {
      fail(`Login failed for student ${id} (HTTP ${response.status}); check credentials and AUTH_MODE`);
    }
    tokenCache[key] = body.accessToken;
  }
  return { Authorization: 'Bearer ' + tokenCache[key] };
}

function isPage(body) {
  return body && Array.isArray(body.content) && Number.isInteger(body.totalElements);
}

function hasNoScheduleOverlap(enrollments) {
  const slots = [];
  for (const enrollment of enrollments) {
    if (!Array.isArray(enrollment?.course?.schedules)) return false;
    for (const slot of enrollment.course.schedules) {
      if (!Number.isInteger(slot.weekday) || !/^\d{2}:\d{2}$/.test(slot.startTime) ||
          !/^\d{2}:\d{2}$/.test(slot.endTime) || slot.startTime >= slot.endTime) return false;
      if (slots.some((other) => other.weekday === slot.weekday &&
          slot.startTime < other.endTime && other.startTime < slot.endTime)) return false;
      slots.push(slot);
    }
  }
  return true;
}

// LT-01: list and keyword search through the implemented course API.
function checkCourseListSearch() {
  const list = get('/api/courses?size=20');
  const listBody = json(list);
  check(list, {
    'LT-01 course list returns a page': (r) =>
      r.status === 200 && isPage(listBody) && listBody.content.length > 0,
  });

  const search = get('/api/courses?keyword=' + encodeURIComponent(searchKeyword) + '&size=20');
  const searchBody = json(search);
  const keyword = searchKeyword.trim().toLocaleLowerCase();
  check(search, {
    'LT-01 keyword search returns matching courses': (r) =>
      r.status === 200 && isPage(searchBody) && searchBody.content.length > 0 &&
      searchBody.content.every((course) =>
        [course.courseName, course.courseCode, course.professorName]
          .some((value) => typeof value === 'string' && value.toLocaleLowerCase().includes(keyword))),
  });
}

// LT-02: concurrent detail reads for one configurable offering.
function checkHotspotCourse() {
  const response = get('/api/courses/' + hotOfferingId);
  const body = json(response);
  check(response, {
    'LT-02 target offering detail remains valid': (r) =>
      r.status === 200 && body?.offeringId === hotOfferingId &&
      Number.isInteger(body.capacity) && body.capacity > 0 &&
      Number.isInteger(body.enrolledCount) && body.enrolledCount >= 0 &&
      body.full === (body.enrolledCount >= body.capacity),
  });
}

// LT-09: observed enrollment totals and schedules, without a registration request.
function checkStudentConsistency() {
  const headers = authHeaders(studentId);
  const profileResponse = get('/api/students/me', headers);
  const profileBody = json(profileResponse);
  check(profileResponse, {
    'LT-09 student profile is available': (r) => r.status === 200 && profileBody?.studentId === studentId,
  });

  const enrollmentResponse = get('/api/enrollments/me', headers);
  const body = json(enrollmentResponse);
  check(enrollmentResponse, {
    'LT-09 enrollment list is available': (r) =>
      r.status === 200 && body?.studentId === studentId && Array.isArray(body.enrollments) &&
      body.count === body.enrollments.length,
    'LT-09 credit totals and limit are consistent': () =>
      Array.isArray(body?.enrollments) &&
      body.enrollments.every((item) => Number.isInteger(item?.course?.credits) && item.course.credits > 0) &&
      body.totalCredits === body.enrollments.reduce((sum, item) => sum + item.course.credits, 0) &&
      body.totalCredits === profileBody?.currentCredits &&
      body.maxCredits === profileBody?.maxCredits && body.totalCredits <= body.maxCredits,
    'LT-09 existing classes have no schedule overlap': () =>
      Array.isArray(body?.enrollments) && hasNoScheduleOverlap(body.enrollments),
  });
}

export function setup() {
  if (writeProfiles.includes(profile)) {
    const health = get('/actuator/health');
    if (health.status !== 200 || json(health)?.status !== 'UP') {
      fail('Write preflight failed: backend is not healthy');
    }
    const headers = authHeaders(fixtureStart, 0);
    const enrollment = get('/api/enrollments/me', headers);
    const body = json(enrollment);
    const requests = get('/api/exchange-requests/me', headers);
    const requestsBody = json(requests);
    if (enrollment.status !== 200 || body?.studentId !== fixtureStart ||
        body?.enrollments?.length !== 6 || requests.status !== 200 ||
        !requestsBody?.requests?.some((item) => item.requestId === fixtureStart)) {
      fail('Write preflight failed: expected the synthetic course_change_loadtest fixture');
    }
    if (profile === 'lt08') {
      const match = post(`/api/exchange-requests/${fixtureStart}/quick-match`, null, headers);
      const sessionId = json(match)?.session?.sessionId;
      if (match.status !== 200 || !sessionId) {
        fail('LT-08 setup needs a fresh reciprocal pair with WAITING requests');
      }
      return { sessionId };
    }
    return;
  }
  if (profile === 'lt18') {
    const courses = get('/api/courses?size=1');
    const hot = get('/api/courses/' + hotOfferingId);
    const student = get('/api/students/me', authHeaders(studentId));
    if (courses.status !== 200 || !isPage(json(courses)) ||
        hot.status !== 200 || json(hot)?.offeringId !== hotOfferingId ||
        student.status !== 200 || json(student)?.studentId !== studentId) {
      fail('LT-18 preflight failed: check BASE_URL, HOT_OFFERING_ID and STUDENT_ID');
    }
    return;
  }
  const response = get('/actuator/health');
  if (response.status !== 200 || json(response)?.status !== 'UP') {
    fail(`Backend is not healthy at ${baseUrl}/actuator/health (HTTP ${response.status})`);
  }
}

// One deterministic pass through the APIs implemented in wip/day22.
export function apiSmoke() {
  group('course discovery', () => {
    checkCourseListSearch();

    const detail = get('/api/courses/9');
    const detailBody = json(detail);
    check(detail, {
      'seeded offering 9 has its expected schedule': (r) =>
        r.status === 200 && detailBody?.offeringId === 9 &&
        detailBody.scheduleText === '화목 15:00-16:15',
    });

    const filters = get('/api/courses/filters');
    const filtersBody = json(filters);
    check(filters, {
      'course filters are available': (r) => r.status === 200 && Array.isArray(filtersBody?.departments),
    });
  });

  group('student schedule', () => {
    const students = get('/api/students');
    const studentsBody = json(students);
    check(students, {
      'demo students can be selected': (r) => r.status === 200 &&
        Array.isArray(studentsBody) && studentsBody.some((student) => student.studentId === studentId),
    });

    checkStudentConsistency();
  });

  group('documented errors', () => {
    const missingHeader = get('/api/enrollments/me');
    check(missingHeader, {
      'missing authentication is rejected': (r) =>
        r.status === 401 && json(r)?.code ===
          (authMode === 'TOKEN' ? 'MISSING_TOKEN' : 'MISSING_STUDENT_ID'),
    });

    const missingCourse = get('/api/courses/999');
    check(missingCourse, {
      'unknown offering is rejected': (r) =>
        r.status === 404 && json(r)?.code === 'COURSE_NOT_FOUND',
    });

    const invalidPage = get('/api/courses?page=-1');
    check(invalidPage, {
      'invalid page is rejected': (r) =>
        r.status === 400 && json(r)?.code === 'INVALID_PARAMETER',
    });
  });
}

export function courseListSearch() {
  checkCourseListSearch();
  sleep(thinkTime);
}

export function hotspotCourse() {
  checkHotspotCourse();
  sleep(thinkTime);
}

export function studentConsistency() {
  checkStudentConsistency();
  sleep(thinkTime);
}

// Synthetic fixture: student i owns six enrollments. The second one has no
// preloaded exchange request, so registrations do not consume the matching pair.
function secondEnrollment(index) {
  return fixtureStart + index * 6 + 1;
}

function secondTarget(index) {
  const group = Math.floor(index / 2) % 200;
  return fixtureStart + (group * 6 + 1) * 2 + (1 - index % 2);
}

// LT-03: distinct students register requests in a bounded burst.
export function exchangeRegistration() {
  const index = exec.scenario.iterationInTest;
  const id = fixtureStart + index;
  const response = post('/api/exchange-requests', {
    offeredEnrollmentId: secondEnrollment(index),
    wantedOfferingIds: [secondTarget(index)], autoMatch: false,
  }, authHeaders(id, index));
  const body = json(response);
  check(response, { 'LT-03 request entered queue': (r) =>
    r.status === 201 && body?.status === 'WAITING' &&
    body.offeredEnrollmentId === secondEnrollment(index) });
}

// LT-04: all iterations race to register the same student's second enrollment.
export function sameEnrollmentRace() {
  const response = post('/api/exchange-requests', {
    offeredEnrollmentId: secondEnrollment(0),
    wantedOfferingIds: [secondTarget(0)], autoMatch: false,
  }, authHeaders(fixtureStart, 0));
  const body = json(response);
  check(response, { 'LT-04 created once or rejected duplicate': (r) =>
    (r.status === 201 && body?.status === 'WAITING') ||
    (r.status === 409 && body?.code === 'REQUEST_ALREADY_EXISTS') });
}

// LT-05: ranking is recomputed for existing WAITING requests.
export function queueRanking() {
  const index = (__VU - 1) % 10000;
  const requestId = fixtureStart + index;
  const response = get(`/api/exchange-requests/${requestId}/queue`,
    authHeaders(requestId, index));
  const body = json(response);
  check(response, { 'LT-05 waiting request has a valid rank': (r) =>
    r.status === 200 && body?.requestId === requestId &&
    body.status === 'WAITING' && body.position >= 1 &&
    body.competitors >= body.position && Number.isInteger(body.score) });
  sleep(thinkTime);
}

// LT-06: one request per independent reciprocal pair starts quick matching.
export function automaticMatching() {
  const index = exec.scenario.iterationInTest * 2;
  const requestId = fixtureStart + index;
  const response = post(`/api/exchange-requests/${requestId}/quick-match`, null,
    authHeaders(requestId, index));
  const body = json(response);
  check(response, { 'LT-06 reciprocal pair created a session': (r) =>
    r.status === 200 && body?.matched === true &&
    body.session?.status === 'PENDING' && body.session.participants?.length === 2 });
}

// LT-07: competing quick-match requests target one reciprocal pair.
export function samePairRace() {
  const response = post(`/api/exchange-requests/${fixtureStart}/quick-match`, null,
    authHeaders(fixtureStart, 0));
  const body = json(response);
  conflict409.add(response.status === 409 && body?.code === 'EXCHANGE_CONFLICT' ? 1 : 0);
  check(response, { 'LT-07 match or documented race rejection': (r) =>
    (r.status === 200 && body?.matched === true && body.session?.sessionId > 0) ||
    (r.status === 409 && ['EXCHANGE_CONFLICT', 'REQUEST_NOT_MODIFIABLE'].includes(body?.code)) });
}

// LT-08: two participants accept the same session; the second acceptance swaps seats.
export function simultaneousAccept(data) {
  const index = exec.scenario.iterationInTest;
  const id = fixtureStart + index;
  const response = post(`/api/match-sessions/${data.sessionId}/accept`, null,
    authHeaders(id, index));
  const body = json(response);
  check(response, { 'LT-08 acceptance recorded': (r) =>
    r.status === 200 && ['PENDING', 'COMPLETED'].includes(body?.status) });
}

export function teardown(data) {
  if (profile !== 'lt08') return;
  const response = get(`/api/match-sessions/${data.sessionId}`,
    authHeaders(fixtureStart, 0));
  check(response, { 'LT-08 session completed after both acceptances': (r) =>
    r.status === 200 && json(r)?.status === 'COMPLETED' });
}

// LT-11: repeated session, request and wishlist polling through implemented APIs.
export function sessionPolling() {
  const headers = authHeaders(studentId);
  const sessions = get('/api/match-sessions/me', headers);
  check(sessions, { 'LT-11 session list is available': (r) =>
    r.status === 200 && Array.isArray(json(r)) });
  const requests = get('/api/exchange-requests/me', headers);
  check(requests, { 'LT-11 exchange request list is available': (r) =>
    r.status === 200 && Array.isArray(json(r)?.requests) });
  const wishlists = get('/api/wishlists/me', headers);
  check(wishlists, { 'LT-11 wishlist is available': (r) =>
    r.status === 200 && Array.isArray(json(r)?.wishlists) });
  sleep(thinkTime);
}

// LT-12/16/17/18 use the implemented read APIs only.
export function mixedRead() {
  const choice = Math.random();
  if (choice < 0.5) checkCourseListSearch();
  else if (choice < 0.8) checkHotspotCourse();
  else checkStudentConsistency();
  sleep(thinkTime);
}
