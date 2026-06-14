(() => {
  const toggle = document.querySelector(".password-toggle");
  const password = document.getElementById("password");

  if (!toggle || !password) {
    return;
  }

  toggle.addEventListener("click", () => {
    const isVisible = password.type === "text";
    password.type = isVisible ? "password" : "text";
    toggle.setAttribute("aria-pressed", String(!isVisible));
    toggle.setAttribute("aria-label", isVisible ? "Show password" : "Hide password");
    password.focus();
  });
})();
