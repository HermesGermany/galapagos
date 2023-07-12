import { Injectable } from '@angular/core';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';
import { BehaviorSubject, firstValueFrom, Observable, skipWhile, tap } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { HttpClient } from '@angular/common/http';
import { LocationStrategy } from '@angular/common';
import { Router } from '@angular/router';

const emptyUserProfile = { userName: '', displayName: '' };

const loginHandlerUri = '/dashboard';

export interface UserProfile {

    userName: string;

    displayName: string;

}

@Injectable({ providedIn: 'root' })
export class AuthService {

    authenticated: Observable<boolean>;

    admin: Observable<boolean>;

    roles: Observable<string[]>;

    userProfile: Observable<UserProfile>;

    private authenticatedSubject = new BehaviorSubject<boolean>(false);

    private rolesSubject = new BehaviorSubject<string[]>([]);

    private profileSubject = new BehaviorSubject<UserProfile>(emptyUserProfile);

    private userNameClaim: string = null;

    private displayNameClaim: string = null;

    private rolesClaim: string = null;

    private configLoaded = new BehaviorSubject<boolean>(false);

    constructor(private oauthService: OAuthService, private http: HttpClient, private locationStrategy: LocationStrategy,
                private router: Router) {
        this.authenticated = this.authenticatedSubject.asObservable();
        this.roles = this.rolesSubject.asObservable();
        this.admin = this.roles.pipe(map(roles => !!roles.find(r => r.toUpperCase() === 'ADMIN')));
        this.userProfile = this.profileSubject.asObservable();

        this.loadAuthConfig().then(config => {
            this.configure(config);
            this.configLoaded.next(true);
        });

        window.addEventListener('storage', event => {
            // The `key` is `null` if the event was caused by `.clear()`
            if (event.key !== 'access_token' && event.key !== null) {
                return;
            }

            this.checkAuthenticated();
        });
    }

    async tryLoginFromData(): Promise<boolean> {
        await firstValueFrom(this.getConfigLoadedObservable());

        if (this.checkAuthenticated()) {
            return Promise.resolve(true);
        }

        await this.oauthService.tryLoginCodeFlow().catch(e => console.error('Could not extract login information', e));
        if (this.checkAuthenticated()) {
            return this.router.navigateByUrl(decodeURIComponent(this.oauthService.state));
        }

        return Promise.resolve(false);
    }

    async login(targetUrl: string): Promise<boolean> {
        await firstValueFrom(this.getConfigLoadedObservable());
        return this.oauthService.loadDiscoveryDocumentAndLogin({
            state: targetUrl
        });
    }

    public logout() {
        this.oauthService.logOut();
    }

    private getConfigLoadedObservable(): Observable<boolean> {
        return this.configLoaded.asObservable().pipe(skipWhile(v => !v));
    }

    private loadAuthConfig(): Promise<AuthConfig> {
        return firstValueFrom(this.http.get('/oauth2/config.json')
            .pipe(tap((data: any) => this.extractClaimNames(data)))
            .pipe(map((data: any) => ({
                issuer: data.issuerUri,

                tokenEndpoint: data.tokenEndpoint,

                redirectUri: window.location.origin,

                clientId: data.clientId,

                responseType: 'code',

                scope: (data.scope as string[]).join(' '),

                preserveRequestedRoute: false,

                postLogoutRedirectUri: '/logout'
            }))));
    }

    private configure(config: AuthConfig) {
        this.oauthService.configure(config);
        this.oauthService.redirectUri = window.location.origin + this.locationStrategy.prepareExternalUrl(loginHandlerUri);
        this.oauthService.events
            .pipe(filter(e => ['token_received'].includes(e.type)))
            .subscribe(() => this.checkAuthenticated());
        this.oauthService.events.pipe(filter(e => ['logout'].includes(e.type))).subscribe(() => this.handleLogout());

        this.oauthService.setupAutomaticSilentRefresh();
    }

    private extractClaimNames(data: any) {
        this.userNameClaim = data.userNameClaim;
        this.displayNameClaim = data.displayNameClaim;
        this.rolesClaim = data.rolesClaim;
    }

    private checkAuthenticated(): boolean {
        const authenticated = this.oauthService.hasValidAccessToken();
        if (authenticated) {
            this.authenticatedSubject.next(true);
            const jwtClaims = this.jwtClaims(this.oauthService.getAccessToken());
            this.rolesSubject.next(this.extractRoles(jwtClaims));
            this.profileSubject.next(this.extractUserProfile(jwtClaims));
        } else {
            this.authenticatedSubject.next(false);
            this.rolesSubject.next(null);
            this.profileSubject.next(null);
        }

        return authenticated;
    }

    private handleLogout() {
        this.authenticatedSubject.next(false);
        this.profileSubject.next(emptyUserProfile);
        this.rolesSubject.next([]);
    }

    private extractRoles(jwtClaims: object): string[] {
        if (!this.rolesClaim) {
            return [];
        }
        return jwtClaims[this.rolesClaim] || [];
    }

    private extractUserProfile(jwtClaims: object): UserProfile {
        if (!this.userNameClaim || !this.displayNameClaim) {
            console.error('Missing userName or displayName claim names from server config');
            return emptyUserProfile;
        }
        return {
            userName: jwtClaims[this.userNameClaim] || '',
            displayName: jwtClaims[this.displayNameClaim] || ''
        };
    }

    private jwtClaims(accessToken: string): object {
        const tokenParts = accessToken.split('.');
        const claimsBase64 = this.padBase64(tokenParts[1]);
        const claimsJson = atob(claimsBase64);
        return JSON.parse(claimsJson);
    }

    private padBase64(base64data) {
        while (base64data.length % 4 !== 0) {
            base64data += '=';
        }
        return base64data;
    }

}
