package com.hermesworld.ais.galapagos;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the Angular Frontend. Redirects (forwards) all "virtual" paths (routes) of the Angular application to
 * the <code>index.html</code> page.
 *
 * @author AlbrechtFlo
 *
 */
@Controller
public class HomeController {

    // TODO alternative implementation idea: catch /app/**, check if a matching static resource exists. If yes, return
    // it.
    // If no, forward to index.html (not sure if this is possible with a controller only)

    /**
     * Forwards all calls to an Angular route to the <code>index.html</code> page. Note: If new routes are added to the
     * frontend, you will have to add them here as part of the mapping as well.
     *
     * @return Forward command to the <code>index.html</code> page.
     */
    @GetMapping({ "/app", "/app/applications", "/app/admin", "/app/topics", "/app/topics/**", "/app/dashboard",
            "/app/createtopic", "/app/user-settings" })
    public String app() {
        return "forward:/app/index.html";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/app";
    }

}
