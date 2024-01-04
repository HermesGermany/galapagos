import { Component, OnInit } from '@angular/core';
import { ServerInfoService } from '../../../shared/services/serverinfo.service';
import { NavigationEnd, Router } from '@angular/router';
import { Observable } from 'rxjs';
import { EnvironmentsService, KafkaEnvironment } from 'src/app/shared/services/environments.service';
import { map } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../shared/services/auth.service';

@Component({
    selector: 'app-header',
    templateUrl: './header.component.html',
    styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {
    public pushRightClass: string;

    public userName: Observable<string>;

    instanceNameInfo: Observable<string>;

    public currentEnvironmentName: Observable<string>;

    public currentEnvironmentIcon: Observable<string>;

    public allEnvironments: Observable<KafkaEnvironment[]>;

    authenticationMode: Observable<string>;

    darkmodeActive: boolean;

    constructor(private translate: TranslateService, public router: Router, private authService: AuthService,
                private environments: EnvironmentsService, private serverInfoService: ServerInfoService) {

    }

    ngOnInit() {
        this.pushRightClass = 'push-right';

        this.instanceNameInfo = this.serverInfoService.getServerInfo().pipe(map(info => info.galapagos.instanceName));

        this.userName = this.authService.userProfile.pipe(map(profile => profile.displayName));

        this.router.events.subscribe(val => {
            if (
                val instanceof NavigationEnd &&
                window.innerWidth <= 992 &&
                this.isToggled()
            ) {
                this.toggleSidebar();
            }
        });

        if (window.matchMedia &&
            window.matchMedia('(prefers-color-scheme: dark)').matches &&
            localStorage.getItem('galapagos.darkmode') === null) {
            this.changeDarkmode(false);
        } else {
            this.changeDarkmode(localStorage.getItem('galapagos.darkmode') !== 'true');
        }

        this.currentEnvironmentName = this.environments.getCurrentEnvironment().pipe(map(env => env.name));
        this.currentEnvironmentIcon = this.environments.getCurrentEnvironment().pipe(
            map(env => env.production ? 'fas fa-exclamation-triangle text-danger' : 'fas fa-database'));
        this.allEnvironments = this.environments.getEnvironments();

        this.authenticationMode = this.environments.getCurrentEnvironment().pipe(map(env => env.authenticationMode));
    }

    isToggled(): boolean {
        const dom: Element = document.querySelector('body');
        return dom.classList.contains(this.pushRightClass);
    }

    toggleSidebar() {
        const dom: any = document.querySelector('body');
        dom.classList.toggle(this.pushRightClass);
    }

    async onLoggedout() {
        return this.authService.logout();
    }

    changeLang(language: string) {
        this.translate.use(language);
    }

    selectEnvironment(env: KafkaEnvironment) {
        this.environments.setCurrentEnvironment(env);
    }

    darkmodeButton(): void {
        this.changeDarkmode(localStorage.getItem('galapagos.darkmode') === 'true');
    }

    changeDarkmode(darkmode: boolean): void {
        if (darkmode) {
            document.documentElement.classList.remove('dark');
            localStorage.setItem('galapagos.darkmode', 'false');
            this.darkmodeActive = false;
        } else {
            document.documentElement.classList.add('dark');
            localStorage.setItem('galapagos.darkmode', 'true');
            this.darkmodeActive = true;
        }
    }


}
