package org.apereo.cas.mgmt.config;

import org.apereo.cas.configuration.CasManagementConfigurationProperties;
import org.apereo.cas.mgmt.DashboardController;
import org.apereo.cas.mgmt.SessionsController;
import org.apereo.cas.mgmt.TokensController;
import org.apereo.cas.mgmt.authentication.CasUserProfileFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;

/**
 * Class used to configure beans used by the /dashboard endpoint.
 *
 * @author Travis Schmidt
 * @since 6.0
 */
public class CasManagementDashboardConfiguration {

    @Autowired
    private CasManagementConfigurationProperties managementProperties;

    @Autowired
    @Qualifier("casUserProfileFactory")
    private ObjectProvider<CasUserProfileFactory> casUserProfileFactory;

    @Bean
    public DashboardController dashboardController() {
        return new DashboardController(managementProperties);
    }

    @Bean
    public SessionsController sessionsController() {
        return new SessionsController(managementProperties, casUserProfileFactory.getIfAvailable());
    }

    @Bean
    public TokensController tokensController() {
        return new TokensController(managementProperties, casUserProfileFactory.getIfAvailable());
    }
}
