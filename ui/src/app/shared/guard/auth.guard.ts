import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { firstValueFrom, map } from 'rxjs';


@Injectable()
export class AuthGuard implements CanActivate {

    constructor(private router: Router, private authService: AuthService) {
    }

    async canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Promise<boolean> {
        const authenticated = await this.authService.tryLoginFromData();

        if (!authenticated) {
            return this.authService.login(state.url);
        }

        const requiredRoles: string[] = route.data.roles;

        if (!requiredRoles || requiredRoles.length === 0) {
            return Promise.resolve(true);
        }
        return this.checkRoles(requiredRoles);
    }

    private checkRoles(requiredRoles: string[]): Promise<boolean> {
        return firstValueFrom(this.authService.roles.pipe(map(roles => requiredRoles.every(role => roles.indexOf(role) > -1))));
    }

}
