package com.hermesworld.ais.galapagos;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.util.ObjectUtils;
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

    /**
     * Forwards all calls to an Angular route to the <code>index.html</code> page. Note: If new routes are added to the
     * frontend, you will have to add them here as part of the mapping as well.
     *
     * @return Forward command to the <code>index.html</code> page.
     */
    @GetMapping({ "/app/applications", "/app/admin", "/app/topics", "/app/topics/**", "/app/dashboard",
            "/app/createtopic", "/app/user-settings" })
    public String app(HttpServletRequest request) {
        if (!ObjectUtils.isEmpty(request.getQueryString())) {
            return "forward:/app/index.html?" + request.getQueryString();
        }
        return "forward:/app/index.html";
    }

    @GetMapping("/app")
    public String appRoot() {
        return "redirect:/app/dashboard";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/app/dashboard";
    }

}
