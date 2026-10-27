import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';

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

const profiles = ['smoke', 'load', 'lt01', 'lt02', 'lt09', 'lt12', 'lt16', 'lt17', 'lt18'];
if (!profiles.includes(profile)) {
  throw new Error('PROFILE must be one of: ' + profiles.join(', '));
}
for (const [name, value] of Object.entries({
  STUDENT_ID: studentId, HOT_OFFERING_ID: hotOfferingId, BROWSE_VUS: browseVus,
  STUDENT_VUS: studentVus, HOT_VUS: hotVus, MIXED_VUS: mixedVus,
  STEP_VUS: stepVus, BASELINE_VUS: baselineVus, SPIKE_VUS: spikeVus,
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

function constant(vus, exec) {
  return { executor: 'constant-vus', vus, duration, exec };
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
    case 'lt09':
      return { scenarios: { student_consistency: constant(studentVus, 'studentConsistency') }, thresholds: loadThresholds() };
    case 'load':
    case 'lt12':
      return {
        scenarios: {
          course_list_search: constant(browseVus, 'courseListSearch'),
          hotspot_detail: constant(hotVus, 'hotspotCourse'),
          student_consistency: constant(studentVus, 'studentConsistency'),
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
  const headers = { 'X-Student-Id': String(studentId) };
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
  if (profile === 'lt18') {
    const courses = get('/api/courses?size=1');
    const hot = get('/api/courses/' + hotOfferingId);
    const student = get('/api/students/me', { 'X-Student-Id': String(studentId) });
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
      'missing student ID is rejected': (r) =>
        r.status === 401 && json(r)?.code === 'MISSING_STUDENT_ID',
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

// LT-12/16/17/18 use the implemented read APIs only.
export function mixedRead() {
  const choice = Math.random();
  if (choice < 0.5) checkCourseListSearch();
  else if (choice < 0.8) checkHotspotCourse();
  else checkStudentConsistency();
  sleep(thinkTime);
}
