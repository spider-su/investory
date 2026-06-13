document.addEventListener("DOMContentLoaded", function () {
    const form = document.querySelector("form");
    const content = document.getElementById("content");
    const spinner = document.getElementById("spinner");

    if (form && content && spinner) {
        form.addEventListener("submit", function () {
            content.classList.add("d-none");
            spinner.classList.remove("d-none");
        });
    }
});
