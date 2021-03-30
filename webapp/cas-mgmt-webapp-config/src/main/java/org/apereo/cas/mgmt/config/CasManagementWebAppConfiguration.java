package org.apereo.cas.mgmt.config;

import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.CasManagementConfigurationProperties;
import org.apereo.cas.mgmt.controller.ViewController;
import org.apereo.cas.mgmt.web.DefaultCasManagementEventListener;
import org.apereo.cas.oidc.claims.BaseOidcScopeAttributeReleasePolicy;
import org.apereo.cas.oidc.claims.OidcCustomScopeAttributeReleasePolicy;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.services.resource.RegisteredServiceResourceNamingStrategy;
import org.apereo.cas.util.CollectionUtils;
import lombok.val;
import org.pac4j.cas.client.CasClient;
import org.pac4j.cas.config.CasConfiguration;
import org.pac4j.core.authorization.authorizer.Authorizer;
import org.pac4j.core.authorization.generator.AuthorizationGenerator;
import org.pac4j.core.client.Client;
import org.pac4j.core.config.Config;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.mvc.Controller;
import org.springframework.web.servlet.mvc.ParameterizableViewController;
import org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter;
import org.springframework.web.servlet.mvc.UrlFilenameViewController;
import org.springframework.web.servlet.view.RedirectView;
import org.thymeleaf.spring5.templateresolver.SpringResourceTemplateResolver;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * This is {@link CasManagementWebAppConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 5.0.0
 */
@Configuration("casManagementWebAppConfiguration")
@EnableConfigurationProperties({CasConfigurationProperties.class, CasManagementConfigurationProperties.class})
public class CasManagementWebAppConfiguration implements WebMvcConfigurer {

    @Autowired
    private ServerProperties serverProperties;

    @Autowired
    private ApplicationContext context;

    @Autowired
    @Qualifier("webApplicationServiceFactory")
    private ServiceFactory<WebApplicationService> webApplicationServiceFactory;

    @Autowired
    @Qualifier("authenticationClients")
    private List<Client> authenticationClients;

    @Autowired
    @Qualifier("authorizationGenerator")
    private ObjectProvider<AuthorizationGenerator> authorizationGenerators;

    @Autowired
    @Qualifier("managementWebappAuthorizer")
    private ObjectProvider<Authorizer> managementWebappAuthorizer;

    @Autowired
    private CasConfigurationProperties casProperties;

    @Autowired
    private CasManagementConfigurationProperties managementProperties;

    @Bean
    protected Controller rootController() {
        return new ParameterizableViewController() {
            @Override
            protected ModelAndView handleRequestInternal(final HttpServletRequest request,
                                                         final HttpServletResponse response) {
                val url = request.getContextPath() + '/';
                return new ModelAndView(new RedirectView(url));
            }

        };
    }

    @Bean
    @Lazy
    public SimpleUrlHandlerMapping handlerMapping() {
        val mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(0);
        mapping.setAlwaysUseFullPath(true);
        mapping.setRootHandler(rootController());

        val properties = new Properties();
        properties.put("/*.html", new UrlFilenameViewController());
        mapping.setMappings(properties);
        return mapping;
    }

    @ConditionalOnMissingBean(name = "localeResolver")
    @Bean
    public LocaleResolver casManagementLocaleResolver() {
        return new CookieLocaleResolver() {
            @Override
            protected Locale determineDefaultLocale(final HttpServletRequest request) {
                val locale = request.getLocale();
                if (StringUtils.isEmpty(managementProperties.getDefaultLocale())
                    || !locale.getLanguage().equals(managementProperties.getDefaultLocale())) {
                    return locale;
                }
                return new Locale(managementProperties.getDefaultLocale());
            }
        };
    }

    @RefreshScope
    @Bean(name = "localeChangeInterceptor")
    public HandlerInterceptor casManagementLocaleChangeInterceptor() {
        val bean = new LocaleChangeInterceptor();
        bean.setParamName(this.casProperties.getLocale().getParamName());
        return bean;
    }

    @Bean
    public SimpleControllerHandlerAdapter casManagementSimpleControllerHandlerAdapter() {
        return new SimpleControllerHandlerAdapter();
    }

    @RefreshScope
    @Bean
    public Collection<BaseOidcScopeAttributeReleasePolicy> userDefinedScopeBasedAttributeReleasePolicies() {
        val oidc = casProperties.getAuthn().getOidc();
        return oidc.getUserDefinedScopes().entrySet()
            .stream()
            .map(k -> new OidcCustomScopeAttributeReleasePolicy(k.getKey(), CollectionUtils.wrapList(k.getValue().split(","))))
            .collect(Collectors.toSet());
    }

    @Bean
    @Lazy
    protected UrlFilenameViewController passThroughController() {
        return new UrlFilenameViewController();
    }

    @RefreshScope
    @Bean
    public DefaultCasManagementEventListener defaultCasManagementEventListener() {
        return new DefaultCasManagementEventListener();
    }

    @Bean(name = "namingStrategy")
    @RefreshScope
    public RegisteredServiceResourceNamingStrategy registeredServiceResourceNamingStrategy() {
        return new RegisteredServiceResourceNamingStrategy() {
            @Override
            public String build(final RegisteredService service, final String extenstion) {
                return "service-" + service.getId() + ".json";
            }
        };
    }

    @Bean
    public SpringResourceTemplateResolver casManagementTemplateResolver() {
        val resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(this.context);
        resolver.setPrefix("classpath:/dist/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);
        resolver.setOrder(1);
        resolver.setCheckExistence(true);
        resolver.setResolvablePatterns(CollectionUtils.wrapHashSet("management/**"));
        return resolver;
    }

    @Bean SpringResourceTemplateResolver staticTemplateResolver() {
        val resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(this.context);
        resolver.setPrefix("classpath:/dist/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);
        resolver.setOrder(1);
        resolver.setCheckExistence(true);
        resolver.setResolvablePatterns(CollectionUtils.wrapHashSet("register/**"));
        return resolver;
    }

    @Bean SpringResourceTemplateResolver dashboardTemplateResolver() {
        val resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(this.context);
        resolver.setPrefix("classpath:/dist/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);
        resolver.setOrder(2);
        resolver.setCheckExistence(true);
        resolver.setResolvablePatterns(CollectionUtils.wrapHashSet("dashboard/**"));
        return resolver;
    }


    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/dist/", "classpath:/static/");
    }

    @Bean
    public ViewController viewController() {
        val defaultCallbackUrl = getDefaultCallbackUrl(casProperties, serverProperties);
        return new ViewController(webApplicationServiceFactory.createService(defaultCallbackUrl));
    }

    @ConditionalOnMissingBean(name = "casManagementSecurityConfiguration")
    @Bean
    public Config casManagementSecurityConfiguration() {
        final CasConfiguration casConfiguration = new CasConfiguration("https://ssodev.ucdavis.edu/cas/login");
        final CasClient casClient = new CasClient(casConfiguration);
        casClient.addAuthorizationGenerator(authorizationGenerators.getIfAvailable());
        val cfg = new Config("https://localhost:8444/callback", List.of(casClient));
        cfg.setAuthorizer(this.managementWebappAuthorizer.getIfAvailable());
        return cfg;
        /*
        val cfg = new Config(getDefaultCallbackUrl(casProperties, serverProperties), authenticationClients);
        cfg.setAuthorizer(this.managementWebappAuthorizer.getIfAvailable());
        return cfg;
         */
    }

    /**
     * Gets default callback url.
     *
     * @param casProperties    the cas properties
     * @param serverProperties the server properties
     * @return the default callback url
     */
    public String getDefaultCallbackUrl(final CasConfigurationProperties casProperties, final ServerProperties serverProperties) {
        try {
            return managementProperties.getServerName().concat(serverProperties.getServlet().getContextPath()).concat("management/index.html");
        } catch (final Exception e) {
            throw new BeanCreationException(e.getMessage(), e);
        }
    }

}
