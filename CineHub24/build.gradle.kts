// version is bumped automatically on each `make` build; you can also set it manually
version = 1

cloudstream {
    // Where CloudStream tells users to go for updates / issues.
    // Replace with your actual GitHub repo once you publish.
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/yourname/your-repo")

    description = "Watch movies from CineHub24"
    authors = listOf("YourName")

    /**
     * Status int as used by CloudStream:
     * 0 = Down
     * 1 = Ok
     * 2 = Slow
     * 3 = Beta only
     */
    status = 3 // start at Beta until you've confirmed it works reliably

    tvTypes = listOf("Movie")

    // Favicon fallback — CloudStream will fetch and cache this
    iconUrl = "https://www.google.com/s2/favicons?domain=cinehub24.com&sz=%size%"
}
