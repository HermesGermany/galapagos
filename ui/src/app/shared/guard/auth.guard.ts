import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot } from '@angular/router';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';

const authConfig: AuthConfig = {
    //TODO load from backend
    showDebugInformation: true,
    issuer: 'https://accounts.google.com',
    redirectUri: 'http://localhost:4200/',
    clientId: '354145891472-5m9ddeqipfpnmnqg32oguglidqc4de5n.apps.googleusercontent.com',
    scope: 'openid profile email',
    strictDiscoveryDocumentValidation: false
};

@Injectable()
export class AuthGuard implements CanActivate {

    constructor(private oauthService: OAuthService) {
        this.oauthService.configure(authConfig);
    }

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
        if (this.oauthService.hasValidAccessToken()) {
            return Promise.resolve(true);
        }
        return this.oauthService.loadDiscoveryDocumentAndLogin().then(() => {
            this.oauthService.initCodeFlow();

            this.oauthService.loadUserProfile().then(user => {
                console.log(this.oauthService.getIdToken());
                console.log(this.oauthService.getAccessToken());
                console.log(user);
                //   const info = this.jwtHelper.decodeToken(this.oauthService.getIdToken());
                //   console.log(info);
                // this.userSubject.next(info);
                // this.rolesSubject.next(info['resource_access'].webapp.roles);
            });
        }).then(() => true);
    }
}
