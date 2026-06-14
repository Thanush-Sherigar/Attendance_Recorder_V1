/* ==========================================================================
   SMART ATTENDANCE PRO - APP LOGIC & BACKEND INTEGRATION
   ========================================================================== */

// --- GLOBAL STATE ---
const API_BASE = window.location.origin;
let teacherToken = sessionStorage.getItem('teacher_token') || null;
let studentToken = sessionStorage.getItem('student_token') || null;
let activeSession = null;
let sessionTimer = null;
let currentScreen = 'screen-student-login';
let simulatedStudentDeviceId = 'device-pixel9-1ds24mc108'; // Default mock device ID

// Document Ready
document.addEventListener('DOMContentLoaded', () => {
    // Start clock updates on simulated phone status bar
    updatePhoneClock();
    setInterval(updatePhoneClock, 1000);

    // Initial check of backend health
    checkBackendHealth();

    // Setup event listeners
    setupEventListeners();

    // Check existing auth states
    restoreSessions();
});

// Update Phone Clock (HH:MM format)
function updatePhoneClock() {
    const clockEl = document.getElementById('phone-clock');
    if (clockEl) {
        const now = new Date();
        let hours = now.getHours();
        let minutes = now.getMinutes();
        hours = hours < 10 ? '0' + hours : hours;
        minutes = minutes < 10 ? '0' + minutes : minutes;
        clockEl.textContent = `${hours}:${minutes}`;
    }
}

// Check if backend is alive
async function checkBackendHealth() {
    const statusText = document.getElementById('backend-status-text');
    const indicator = document.querySelector('.status-indicator');
    
    try {
        // Ping auth endpoints or root as health check
        const response = await fetch(`${API_BASE}/error`, { method: 'GET' });
        if (response.status === 404 || response.status === 200 || response.status === 401 || response.status === 403) {
            statusText.textContent = "Backend Connected";
            indicator.className = "status-indicator online";
        }
    } catch (e) {
        statusText.textContent = "Backend Offline (Run Maven server)";
        indicator.className = "status-indicator offline";
    }
}

// Restore sessions from storage
function restoreSessions() {
    if (teacherToken) {
        showTeacherDashboard();
        fetchMySessions();
    }
    if (studentToken) {
        showStudentScreen('screen-student-scanner');
        document.getElementById('student-display-name').textContent = sessionStorage.getItem('student_name') || 'Student';
    }
}

// --- PORTAL NAVIGATION & UI TOGGLES ---
function toggleTeacherRegister(showRegister) {
    if (showRegister) {
        document.getElementById('teacher-login-card').classList.add('hidden');
        document.getElementById('teacher-register-card').classList.remove('hidden');
    } else {
        document.getElementById('teacher-login-card').classList.remove('hidden');
        document.getElementById('teacher-register-card').classList.add('hidden');
    }
}

function showStudentRegister(showReg) {
    if (showReg) {
        showStudentScreen('screen-student-register');
    } else {
        showStudentScreen('screen-student-login');
    }
}

function showStudentScreen(screenId) {
    const screens = ['screen-student-login', 'screen-student-register', 'screen-student-scanner', 'screen-student-history'];
    screens.forEach(s => {
        const el = document.getElementById(s);
        if (el) {
            if (s === screenId) {
                el.classList.add('active');
            } else {
                el.classList.remove('active');
            }
        }
    });
    currentScreen = screenId;
}

// --- TEACHER FLOW ---
async function teacherLogin() {
    const usernameInput = document.getElementById('teacher-username').value;
    const passwordInput = document.getElementById('teacher-password').value;

    try {
        const response = await fetch(`${API_BASE}/api/auth/signin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: usernameInput, password: passwordInput })
        });

        const data = await response.json();
        if (response.ok) {
            teacherToken = data.accessToken;
            sessionStorage.setItem('teacher_token', teacherToken);
            sessionStorage.setItem('teacher_name', data.username);
            showTeacherDashboard();
            fetchMySessions();
        } else {
            alert(`Login Failed: ${data.message || 'Check username/password'}`);
        }
    } catch (e) {
        alert("Server communication error during login.");
    }
}

async function teacherRegister() {
    const usernameInput = document.getElementById('teacher-reg-username').value;
    const passwordInput = document.getElementById('teacher-reg-password').value;

    try {
        const response = await fetch(`${API_BASE}/api/auth/signup`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: usernameInput, password: passwordInput, role: 'teacher' })
        });

        const data = await response.json();
        if (response.ok) {
            alert("Teacher account registered successfully! You can now log in.");
            toggleTeacherRegister(false);
            document.getElementById('teacher-username').value = usernameInput;
        } else {
            alert(`Registration Failed: ${data.message}`);
        }
    } catch (e) {
        alert("Server communication error during registration.");
    }
}

function showTeacherDashboard() {
    document.getElementById('teacher-login-card').classList.add('hidden');
    document.getElementById('teacher-register-card').classList.add('hidden');
    document.getElementById('teacher-dashboard-card').classList.remove('hidden');
    
    const name = sessionStorage.getItem('teacher_name') || 'Faculty Member';
    document.getElementById('teacher-profile-name').textContent = name;
}

function teacherLogout() {
    teacherToken = null;
    sessionStorage.removeItem('teacher_token');
    sessionStorage.removeItem('teacher_name');
    if (sessionTimer) clearInterval(sessionTimer);
    activeSession = null;
    
    document.getElementById('teacher-dashboard-card').classList.add('hidden');
    document.getElementById('teacher-login-card').classList.remove('hidden');
    document.getElementById('session-active-view').classList.add('hidden');
    document.getElementById('session-setup-view').classList.remove('hidden');
}

// Fetch active sessions for the teacher
async function fetchMySessions() {
    try {
        const response = await fetch(`${API_BASE}/api/sessions/my-sessions`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${teacherToken}` }
        });

        if (response.ok) {
            const sessions = await response.json();
            if (sessions.length > 0) {
                // Restore active session
                displayActiveSession(sessions[0]);
            }
        }
    } catch (e) {
        console.error("Error fetching active sessions:", e);
    }
}

// Start a new session
async function startSession() {
    const subject = document.getElementById('session-course').value;

    try {
        const response = await fetch(`${API_BASE}/api/sessions/start`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${teacherToken}`
            },
            body: JSON.stringify({ subject: subject })
        });

        if (response.ok) {
            const sessionData = await response.json();
            displayActiveSession(sessionData);
        } else {
            alert("Error initiating session.");
        }
    } catch (e) {
        alert("Server communication error starting session.");
    }
}

// Display active session and start QR rotation countdown
function displayActiveSession(session) {
    activeSession = session;
    document.getElementById('session-setup-view').classList.add('hidden');
    document.getElementById('session-active-view').classList.remove('hidden');

    // Populate metadata
    document.getElementById('session-id-display').textContent = session.id;
    document.getElementById('session-course-display').textContent = session.subject;
    
    // Format Start Time
    const startTime = new Date(session.startTime);
    const timeOptions = { hour: 'numeric', minute: 'numeric', hour12: true };
    const dateOptions = { day: 'numeric', month: 'short', year: 'numeric' };
    const formattedStart = `${startTime.toLocaleDateString('en-GB', dateOptions)}, ${startTime.toLocaleTimeString('en-US', timeOptions)}`;
    document.getElementById('session-start-display').textContent = formattedStart;

    // Generate QR code
    generateSessionQr(session.currentQrToken);

    // Initialize Countdown Timer
    startQrCountdown(new Date(session.qrExpiration));
}

// Draw the QR Code using QRious
function generateSessionQr(token) {
    const canvas = document.getElementById('session-qr-canvas');
    new QRious({
        element: canvas,
        value: token,
        size: 180,
        level: 'H',
        background: '#ffffff',
        foreground: '#0f172a'
    });
}

// Force rotate the QR token
async function refreshQrToken() {
    if (!activeSession) return;
    
    try {
        const response = await fetch(`${API_BASE}/api/sessions/refresh/${activeSession.id}`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${teacherToken}` }
        });

        if (response.ok) {
            const updatedSession = await response.json();
            activeSession = updatedSession;
            generateSessionQr(updatedSession.currentQrToken);
            startQrCountdown(new Date(updatedSession.qrExpiration));
        } else {
            alert("Failed to rotate QR token.");
        }
    } catch (e) {
        alert("Network error rotating token.");
    }
}

// Start standard countdown timer
function startQrCountdown(expirationDate) {
    if (sessionTimer) clearInterval(sessionTimer);

    const timerBadges = [
        document.getElementById('session-timer-badge'),
        document.getElementById('session-expires-display')
    ];

    function updateTimer() {
        const now = new Date();
        const diff = expirationDate - now;

        if (diff <= 0) {
            clearInterval(sessionTimer);
            timerBadges.forEach(b => {
                b.textContent = "Expired";
                b.classList.add('countdown-warning');
            });
            // Automatically refresh/rotate token when it expires to keep session fresh
            refreshQrToken();
            return;
        }

        const minutes = Math.floor(diff / 60000);
        const seconds = Math.floor((diff % 60000) / 1000);
        const formattedTime = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
        
        timerBadges.forEach(b => {
            b.textContent = formattedTime;
            if (minutes === 0 && seconds < 15) {
                b.classList.add('countdown-warning');
            } else {
                b.classList.remove('countdown-warning');
            }
        });
    }

    updateTimer();
    sessionTimer = setInterval(updateTimer, 1000);
}

// End attendance session
async function endSession() {
    if (!activeSession) return;

    try {
        const response = await fetch(`${API_BASE}/api/sessions/end/${activeSession.id}`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${teacherToken}` }
        });

        if (response.ok) {
            if (sessionTimer) clearInterval(sessionTimer);
            activeSession = null;
            document.getElementById('session-active-view').classList.add('hidden');
            document.getElementById('session-setup-view').classList.remove('remove');
            document.getElementById('session-setup-view').classList.remove('hidden');
            alert("Attendance session ended successfully.");
        }
    } catch (e) {
        alert("Error ending session.");
    }
}


// --- STUDENT FLOW ---
async function studentLogin() {
    const email = document.getElementById('student-username').value;
    const password = document.getElementById('student-password').value;

    try {
        const response = await fetch(`${API_BASE}/api/auth/signin`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: email, password: password })
        });

        const data = await response.json();
        if (response.ok) {
            studentToken = data.accessToken;
            sessionStorage.setItem('student_token', studentToken);
            sessionStorage.setItem('student_name', data.username.split('@')[0]); // Use first part of email as display name
            sessionStorage.setItem('student_email', data.username);

            // Display success toast
            const toast = document.getElementById('login-success-toast');
            toast.classList.remove('hidden');

            setTimeout(() => {
                toast.classList.add('hidden');
                document.getElementById('student-display-name').textContent = data.username.split('@')[0];
                showStudentScreen('screen-student-scanner');
            }, 1500);
        } else {
            alert(`Student Login Failed: ${data.message || 'Invalid credentials'}`);
        }
    } catch (e) {
        alert("Error connecting to server.");
    }
}

async function studentRegister() {
    const email = document.getElementById('student-reg-username').value;
    const password = document.getElementById('student-reg-password').value;

    try {
        const response = await fetch(`${API_BASE}/api/auth/signup`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: email, password: password, role: 'student' })
        });

        const data = await response.json();
        if (response.ok) {
            alert("Student registered successfully! Please log in.");
            showStudentScreen('screen-student-login');
            document.getElementById('student-username').value = email;
        } else {
            alert(`Registration Failed: ${data.message}`);
        }
    } catch (e) {
        alert("Error connecting to server.");
    }
}

function studentLogout() {
    studentToken = null;
    sessionStorage.removeItem('student_token');
    sessionStorage.removeItem('student_name');
    sessionStorage.removeItem('student_email');
    showStudentScreen('screen-student-login');
}

// Simulate Scanning the Active QR Code
async function simulateQrScan() {
    if (!studentToken) {
        alert("Please log in as a student first.");
        return;
    }

    // Capture token from active session (globally simulated)
    const token = activeSession ? activeSession.currentQrToken : null;
    
    // Check if session exists on screen
    if (!token) {
        // Simulate scanning empty/expired state
        showPhoneErrorDialog("Cannot Mark Attendance", "Error: Invalid or expired QR code.");
        return;
    }

    // Call backend `/api/attendance/mark` endpoint with token, deviceId, and JWT header
    try {
        const response = await fetch(`${API_BASE}/api/attendance/mark?token=${token}&deviceId=${simulatedStudentDeviceId}`, {
            method: 'POST',
            headers: {
                'Authorization': `Bearer ${studentToken}`
            }
        });

        const text = await response.text();
        
        // Try parsing as JSON first, otherwise fallback to text
        let data = {};
        try {
            data = JSON.parse(text);
        } catch(e) {
            data = { message: text };
        }

        if (response.ok) {
            showPhoneSuccessDialog();
            // Refresh history view if it's active
            if (currentScreen === 'screen-student-history') {
                fetchAttendanceHistory();
            }
        } else {
            // Handle specific error codes matching report
            const status = response.status;
            if (status === 410) {
                // Page 34 TTL violation alert
                const now = new Date();
                const currentFormatted = now.toLocaleTimeString('en-US', { hour: 'numeric', minute: 'numeric', hour12: true });
                
                // Allowed time is start_time to start_time + 5 mins
                const startTime = new Date(activeSession.startTime);
                const endTime = new Date(startTime.getTime() + 5 * 60 * 1000);
                const formatTimeOpts = { hour: 'numeric', minute: 'numeric', hour12: true };
                const allowedFormatted = `${startTime.toLocaleTimeString('en-US', formatTimeOpts)} - ${endTime.toLocaleTimeString('en-US', formatTimeOpts)}`;

                showPhoneTimeWindowDialog(allowedFormatted, currentFormatted);
            } else {
                showPhoneErrorDialog("Cannot Mark Attendance", data.message || "Attendance validation failed.");
            }
        }
    } catch (e) {
        showPhoneErrorDialog("Cannot Mark Attendance", "Error: Network communication failed.");
    }
}

// Fetch and render student attendance history (Page 32 Layout)
async function fetchAttendanceHistory() {
    const listContainer = document.getElementById('attendance-history-list');
    listContainer.innerHTML = '<div class="history-empty-state">Loading logs...</div>';

    try {
        const response = await fetch(`${API_BASE}/api/attendance/history`, {
            method: 'GET',
            headers: { 'Authorization': `Bearer ${studentToken}` }
        });

        if (response.ok) {
            const records = await response.json();
            listContainer.innerHTML = '';
            
            if (records.length === 0) {
                listContainer.innerHTML = `
                    <div class="history-empty-state">
                        <span>📂</span>
                        <p>No attendance records found.</p>
                    </div>`;
                return;
            }

            records.forEach(r => {
                const card = document.createElement('div');
                card.className = 'attendance-card-item';
                card.innerHTML = `
                    <div class="subject-icon-circle">📚</div>
                    <div class="attendance-card-info">
                        <h4>${r.subject}</h4>
                        <div class="card-meta-detail">
                            <span>📅 ${r.date}</span>
                            <span>⏰ ${r.time}</span>
                        </div>
                    </div>
                    <span class="status-badge-present">${r.status}</span>
                `;
                listContainer.appendChild(card);
            });
        } else {
            listContainer.innerHTML = '<div class="history-empty-state">Failed to load history logs.</div>';
        }
    } catch (e) {
        listContainer.innerHTML = '<div class="history-empty-state">Network error loading logs.</div>';
    }
}

// --- PHONE DIALOG TRIGGERS ---
function showPhoneSuccessDialog() {
    const overlay = document.getElementById('phone-dialog-overlay');
    const dialog = document.getElementById('dialog-success');
    
    overlay.classList.remove('hidden');
    dialog.classList.remove('hidden');
    document.getElementById('dialog-window-closed').classList.add('hidden');
    document.getElementById('dialog-general-error').classList.add('hidden');
}

function showPhoneTimeWindowDialog(allowedTime, currentTime) {
    const overlay = document.getElementById('phone-dialog-overlay');
    const dialog = document.getElementById('dialog-window-closed');
    
    document.getElementById('dialog-allowed-time').textContent = allowedTime;
    document.getElementById('dialog-current-time').textContent = currentTime;

    overlay.classList.remove('hidden');
    dialog.classList.remove('hidden');
    document.getElementById('dialog-success').classList.add('hidden');
    document.getElementById('dialog-general-error').classList.add('hidden');
}

function showPhoneErrorDialog(title, message) {
    const overlay = document.getElementById('phone-dialog-overlay');
    const dialog = document.getElementById('dialog-general-error');
    
    document.getElementById('dialog-error-title').textContent = title;
    document.getElementById('dialog-error-message').textContent = message;

    overlay.classList.remove('hidden');
    dialog.classList.remove('hidden');
    document.getElementById('dialog-success').classList.add('hidden');
    document.getElementById('dialog-window-closed').classList.add('hidden');
}

function closePhoneDialog() {
    document.getElementById('phone-dialog-overlay').classList.add('hidden');
}

// Phone Bottom Navigation Back Button
function phoneBackNav() {
    if (currentScreen === 'screen-student-history') {
        showStudentScreen('screen-student-scanner');
    } else if (currentScreen === 'screen-student-register') {
        showStudentScreen('screen-student-login');
    }
}

// Phone Bottom Navigation Home Button
function phoneHomeNav() {
    if (studentToken) {
        showStudentScreen('screen-student-scanner');
    } else {
        showStudentScreen('screen-student-login');
    }
}

// --- EVENT LISTENERS ---
function setupEventListeners() {
    // Teacher logins
    document.getElementById('btn-teacher-login').addEventListener('click', teacherLogin);
    document.getElementById('btn-teacher-register').addEventListener('click', teacherRegister);
    document.getElementById('btn-teacher-logout').addEventListener('click', teacherLogout);

    // Session controls
    document.getElementById('btn-start-session').addEventListener('click', startSession);
    document.getElementById('btn-force-refresh-qr').addEventListener('click', refreshQrToken);
    document.getElementById('btn-end-session').addEventListener('click', endSession);

    // Student logins
    document.getElementById('btn-student-login').addEventListener('click', studentLogin);
    document.getElementById('btn-student-register').addEventListener('click', studentRegister);
    document.getElementById('btn-student-logout').addEventListener('click', studentLogout);

    // Student controls
    document.getElementById('btn-open-scanner').addEventListener('click', simulateQrScan);
    document.getElementById('btn-view-history').addEventListener('click', () => {
        showStudentScreen('screen-student-history');
        fetchAttendanceHistory();
    });
    document.getElementById('btn-history-back').addEventListener('click', () => {
        showStudentScreen('screen-student-scanner');
    });

    // Make the simulated scan responsive by clicking on the camera viewfinder
    document.querySelector('.scanner-bracket-frame').addEventListener('click', simulateQrScan);

    // Password view toggle
    document.querySelector('.toggle-password').addEventListener('click', function() {
        const passwordInput = document.getElementById('student-password');
        if (passwordInput.type === 'password') {
            passwordInput.type = 'text';
            this.textContent = '🙈';
        } else {
            passwordInput.type = 'password';
            this.textContent = '👁️';
        }
    });
}
