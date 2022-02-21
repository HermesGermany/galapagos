import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { Router, Routes } from '@angular/router';
import { Location } from '@angular/common';
import { NgZone } from '@angular/core';
import { LayoutComponent } from './layout.component';
import { LanguageTranslationModule } from '../shared/modules/language-translation/language-translation.module';
import { PageHeaderModule } from '../shared';
import { AdminModule } from './admin/admin.module';
import { HeaderComponent } from './components/header/header.component';
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { GalapagosToastComponent } from '../shared/modules/toast/toast.component';
import { ToastService } from '../shared/modules/toast/toast.service';
import { EnvironmentsService } from '../shared/services/environments.service';
import { KeycloakService } from 'keycloak-angular';
import { ApplicationsService } from '../shared/services/applications.service';
import { ServerInfoService } from '../shared/services/serverinfo.service';
import { StagingModule } from './staging/staging.module';
import { AdminComponent } from './admin/admin.component';
import { KeycloakInstance } from 'keycloak-js';

describe('LayoutComponent', () => {
    let component: LayoutComponent;
    let fixture: ComponentFixture<LayoutComponent>;
    let keycloak: KeycloakService;
    const routes: Routes = [
        { path: 'admin', component: AdminComponent }
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                RouterTestingModule.withRoutes(routes),
                TranslateModule.forRoot(),
                LanguageTranslationModule,
                NgbModule,
                HttpClientTestingModule,
                BrowserAnimationsModule,
                PageHeaderModule,
                AdminModule,
                StagingModule
            ],
            declarations: [LayoutComponent, HeaderComponent, SidebarComponent, GalapagosToastComponent],
            providers: [TranslateService,
                KeycloakService,
                Location,
                ToastService,
                EnvironmentsService,
                ApplicationsService,
                ServerInfoService
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(LayoutComponent);
        component = fixture.componentInstance;
        keycloak = fixture.debugElement.injector.get(KeycloakService);
        const token = {
            // eslint-disable-next-line @typescript-eslint/naming-convention
            given_name: 'John',
            // eslint-disable-next-line @typescript-eslint/naming-convention
            family_name: 'Doe'
        };
        const ki: jasmine.SpyObj<KeycloakInstance> = jasmine.createSpyObj([], { idTokenParsed: token });
        spyOn(keycloak, 'getKeycloakInstance').and.returnValue(ki);
    });

    it('should create Layout Component', () => {
        expect(component).toBeTruthy();
    });

    it('should not be null when navigation to administration section as admin', (() => {
        const ngZone = TestBed.get(NgZone);
        const router = TestBed.get(Router);
        spyOn(keycloak, 'getUserRoles').and.returnValue(['admin']);

        ngZone.run(() => router.navigate(['admin'])).then();
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#adminSection'))).not.toBeNull();
    }));
});
