package org.siriusxi.htec.fa.infra.security;

import lombok.extern.log4j.Log4j2;
import org.siriusxi.htec.fa.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.springframework.security.core.context.SecurityContextHolder.MODE_INHERITABLETHREADLOCAL;
import static org.springframework.security.core.context.SecurityContextHolder.setStrategyName;

@Log4j2
@EnableWebSecurity
@EnableGlobalMethodSecurity(
    securedEnabled = true,
    jsr250Enabled = true,
    prePostEnabled = true
)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    private final UserRepository userRepository;
    private final JwtTokenFilter jwtTokenFilter;
    private final String appVersion;
    private final String allowedOrigins;
    
    public SecurityConfig(UserRepository userRepository,
                          JwtTokenFilter jwtTokenFilter,
                          @Value("${app.version:v1}") String appVersion,
                          @Value("${app.allowedOrigins:*}") String allowedOrigins) {
        super();
        
        this.userRepository = userRepository;
        this.jwtTokenFilter = jwtTokenFilter;
        this.appVersion = "/".concat(appVersion);
        this.allowedOrigins = allowedOrigins;
        
        // Inherit security context in async function calls
        setStrategyName(MODE_INHERITABLETHREADLOCAL);
    }
    
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(username ->
                                    userRepository
                                        .findByUsernameIgnoreCase(username)
                                        .orElseThrow(
                                            () -> new UsernameNotFoundException(
                                                format("User: %s, not found",
                                                    username))))
            .passwordEncoder(passwordEncoder());
    }
    
    // Set password encoding schema
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    // Security configurations
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        
        // List of Swagger URLs
        var swaggerAuthList = new String[]{
            appVersion.concat("/api-docs/**"),
            "/webjars/**", "/swagger-ui/**",
            appVersion.concat("/doc/**")};
        
        http
            // Enable CORS
            .cors().and()
            
            //Disable CSRF
            .csrf().disable()
            
            // Set session management to stateless
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            
            // Set unauthorized requests exception handler
            .exceptionHandling()
            .authenticationEntryPoint(
                (request, response, ex) -> {
                    log.error("Unauthorized request - {}", ex.getMessage());
                    response.sendError(SC_UNAUTHORIZED, ex.getMessage());
                })
            .and()
            // Set H2 database console permission
            .authorizeRequests()
            .antMatchers("/db-console/**").permitAll()
            .and()
            // This will allow frames with same origin which is much more safe
            .headers().frameOptions().disable()
            .and()
            
            // Set permissions on endpoints
            .authorizeRequests()
            //Enable root
            .antMatchers("/", "/index.html").permitAll()
            //Enables images
            .antMatchers("/assets/**").permitAll()
            // Swagger endpoints must be publicly accessible
            .antMatchers(swaggerAuthList).permitAll()
            // Our public endpoints
            .antMatchers("/public/**").permitAll()
            //Our private endpoints
            .anyRequest().authenticated()
            .and()
            
            // Add JWT token filter
            .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class);
    }
    
    // Used by spring security if CORS is enabled.
    @Bean
    public CorsFilter corsFilter() {
        var source = new UrlBasedCorsConfigurationSource();
        var config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        
        /*
          When allowCredentials is true, allowedOrigins cannot contain the special value "*" since
          that cannot be set on the "Access-Control-Allow-Origin" response header.
           
          To allow credentials to a set of origins, list them explicitly or consider
          using "allowedOriginPatterns" instead.
         */
        config.addAllowedOrigin(allowedOrigins);
        
        /*
          When we have a client application say Angular, there is a problem will occur when
          reading headers from the client application which is related to the CORS handshaking:
          
          - If the server does not explicitly allow the client application to read the headers,
             the browser will hide them from the client application.
          
          -- Then the solution is that the server must add in its responses the header
             "Access-Control-Expose-Headers:<header_name>,<header-name2>" in order to let client to
              read them. So we use here config.addExposedHeader() method.
         */
        config.addExposedHeader("Authorization");
    
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
    
    // Expose authentication manager bean
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}
