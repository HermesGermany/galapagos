import { Injectable } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
import { ReplaySubject } from 'rxjs';
import { JwtHelperService } from '@auth0/angular-jwt';


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
        return Promise.resolve(null);
    }

    logOut() {
        this.oauthService.logOut();
    }
}
