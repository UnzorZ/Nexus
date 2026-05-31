import React from 'react';
import './NexusLogin.css';

export default function NexusLogin() {
  return (
    <div className="nexus-login-container">
      {/* Left Panel */}
      <div className="login-panel">
        <div className="login-card">
          <div className="logo-container">
            <svg
              className="logo-icon"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <polyline points="4 5 12 11 20 5"></polyline>
              <polyline points="4 11 12 17 20 11"></polyline>
              <polyline points="4 17 12 23 20 17"></polyline>
            </svg>
            <div className="logo-text">
              <h1>NEXUS</h1>
              <span className="subtitle">CONTROL PLANE</span>
            </div>
          </div>

          <div className="header-text">
            <h2>Welcome back</h2>
            <p>Sign in to your Nexus control plane</p>
          </div>

          <form className="login-form">
            <div className="input-group">
              <label>Email</label>
              <div className="input-wrapper">
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <rect width="20" height="16" x="2" y="4" rx="2"></rect>
                  <path d="m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7"></path>
                </svg>
                <input type="email" placeholder="admin@nexus.dev" />
              </div>
            </div>

            <div className="input-group">
              <label>Password</label>
              <div className="input-wrapper">
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <rect width="18" height="11" x="3" y="11" rx="2" ry="2"></rect>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"></path>
                </svg>
                <input type="password" placeholder="••••••••••••" />
                <svg
                  className="eye-icon"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z"></path>
                  <circle cx="12" cy="12" r="3"></circle>
                </svg>
              </div>
            </div>

            <div className="form-actions">
              <label className="remember-me">
                <input type="checkbox" defaultChecked />
                <span>Remember me</span>
              </label>
              <a href="#" className="forgot-password">
                Forgot password?
              </a>
            </div>

            <button type="button" className="sign-in-btn">
              Sign in
            </button>
          </form>

          <div className="admin-notice">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
              <path d="m9 12 2 2 4-4"></path>
            </svg>
            <div className="notice-text">
              <strong>Admin access only</strong>
              <p>This area is restricted to Nexus administrators.</p>
            </div>
          </div>

          <div className="footer">Nexus Control Plane v0.1</div>
        </div>
      </div>

      {/* Right Panel / Illustration */}
      <div className="illustration-panel">
        <div className="bg-circles"></div>

        <div className="nodes-container">
          <svg className="connecting-lines">
            <line x1="300" y1="300" x2="300" y2="100" />
            <circle cx="300" cy="180" r="4" fill="#4f46e5" />

            <line x1="300" y1="300" x2="500" y2="250" />
            <circle cx="420" cy="270" r="4" fill="#4f46e5" />

            <line x1="300" y1="300" x2="450" y2="450" />
            <circle cx="390" cy="390" r="4" fill="#10b981" />

            <line x1="300" y1="300" x2="150" y2="450" />
            <circle cx="210" cy="390" r="4" fill="#4f46e5" />

            <line x1="300" y1="300" x2="100" y2="250" />
            <circle cx="180" cy="270" r="4" fill="#06b6d4" />
          </svg>

          <div className="node projects-node">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
              <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
              <line x1="12" y1="22.08" x2="12" y2="12"></line>
            </svg>
            <span>Projects</span>
          </div>

          <div className="node identity-node">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
            <span>Identity</span>
          </div>

          <div className="node heartbeat-node">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"></polyline>
            </svg>
            <span>Heartbeat</span>
          </div>

          <div className="node permissions-node">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
              <path d="m9 12 2 2 4-4"></path>
            </svg>
            <span>Permissions</span>
          </div>

          <div className="node apikeys-node">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4"></path>
              <path d="m21 2-9.6 9.6"></path>
              <circle cx="7.5" cy="15.5" r="5.5"></circle>
            </svg>
            <span>API Keys</span>
          </div>

          <div className="node center-node">
            {/* Hexagon shape background could be done via clip-path in CSS, here we just use the logo */}
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <polyline points="4 5 12 11 20 5"></polyline>
              <polyline points="4 11 12 17 20 11"></polyline>
              <polyline points="4 17 12 23 20 17"></polyline>
            </svg>
          </div>
        </div>
      </div>
    </div>
  );
}
