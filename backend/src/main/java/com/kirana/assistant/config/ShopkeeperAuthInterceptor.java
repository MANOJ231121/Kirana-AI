package com.kirana.assistant.config;

import com.kirana.assistant.service.auth.ShopkeeperAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Authorization for order management.
 *
 * PUBLIC (customer flows, unguessable IDs or inherently open):
 * - POST /api/orders (place order)
 * - GET /api/orders/{id} (track own order status)
 *
 * PROTECTED (shopkeeper Bearer token required):
 * - GET /api/orders, GET /api/orders?status=.., GET /api/orders/status/..
 * - PATCH/POST /api/orders/{id}/status, DELETE /api/orders/{id}
 */
@Component
public class ShopkeeperAuthInterceptor implements HandlerInterceptor {

    private final ShopkeeperAuthService auth;

    public ShopkeeperAuthInterceptor(ShopkeeperAuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (isPublic(method, uri)) {
            return true;
        }
        String user = auth.userOf(request.getHeader("Authorization"));
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"timestamp\":\"" + java.time.LocalDateTime.now()
                    + "\",\"status\":401,"
                    + "\"message\":\"Shopkeeper login required\","
                    + "\"path\":\"" + uri + "\"}");
            return false;
        }
        request.setAttribute("shopkeeperUser", user);
        return true;
    }

    private boolean isPublic(String method, String uri) {
        if (HttpMethod.OPTIONS.matches(method)) {
            return true; // CORS preflight
        }
        if (HttpMethod.POST.matches(method) && "/api/orders".equals(uri)) {
            return true; // customer places an order
        }
        // Customer tracking their own order: GET /api/orders/{id} only
        // (list + /status/.. + query remain protected).
        if (HttpMethod.GET.matches(method) && uri.startsWith("/api/orders/")
                && !uri.startsWith("/api/orders/status/")) {
            return true;
        }
        return false;
    }
}
