package no.scienta.cfp;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.UrlHandlerFilter;

/**
 * Spring 6 dropped trailing slash matching, but clients call {@code /speakers/} and {@code /speakers/1/}. This strips
 * the slash before the request reaches the controllers (no redirect, so POST bodies survive). It runs after
 * {@link RequestLoggingFilter}, so the log still shows the path the client sent.
 */
@Configuration
class TrailingSlashConfig {

    @Bean
    FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        FilterRegistrationBean<UrlHandlerFilter> registration =
                new FilterRegistrationBean<>(UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
