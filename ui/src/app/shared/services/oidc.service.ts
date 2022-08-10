import { Injectable } from '@angular/core';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';
import { ReplaySubject } from 'rxjs';
import { JwtHelperService } from '@auth0/angular-jwt';

const authConfig: AuthConfig = {
    issuer: 'https://keycloak.a0695.npr.hc.de/auth/realms/galapagos',
    redirectUri: window.location.origin,
    clientId: 'webapp',
    responseType: 'code',
    scope: 'openid profile email'
};

export interface OIDCUserInfo {
    [key: string]: any;

    sub: string;

    email: string;

    name: string;

}

@Injectable({
    providedIn: 'root'
})
export class OidcService {

    userSubject = new ReplaySubject<OIDCUserInfo>();

    rolesSubject = new ReplaySubject<string[]>();

    jwtHelper: JwtHelperService = new JwtHelperService();

    constructor(private oauthService: OAuthService) {
    }

    loadUser(): Promise<void> {
        this.oauthService.configure(authConfig);
        return this.oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
            if (!this.oauthService.hasValidAccessToken()) {
                this.oauthService.initLoginFlow();
            } else {
                this.oauthService.loadUserProfile().then(user => {
                    const info: OIDCUserInfo = this.jwtHelper.decodeToken(this.oauthService.getAccessToken()) as OIDCUserInfo;
                    this.userSubject.next(info);
                    this.rolesSubject.next(info['resource_access'].webapp.roles);
                });
            }
        });
    }

    logOut() {
        this.oauthService.logOut();
    }
}
