import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SidebarComponent } from './sidebar.component';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LanguageTranslationModule } from '../../../shared/modules/language-translation/language-translation.module';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageHeaderModule } from '../../../shared';
import { By } from '@angular/platform-browser';
import { Routes } from '@angular/router';
import { AdminComponent } from '../../admin/admin.component';
import { AdminModule } from '../../admin/admin.module';
import { DashboardModule } from '../../dashboard/dashboard.module';
import { Location } from '@angular/common';
import { ApplicationsService } from '../../../shared/services/applications.service';
import { DashboardComponent } from '../../dashboard/dashboard.component';
import { AuthService } from '../../../shared/services/auth.service';
import { BehaviorSubject } from 'rxjs';
import { MockAuthService } from '../../../shared/util/test-util';

describe('SidebarComponent', () => {
    let component: SidebarComponent;
    let fixture: ComponentFixture<SidebarComponent>;
    const admin = new BehaviorSubject(true);
    const routes: Routes = [
        { path: 'admin', component: AdminComponent },
        { path: 'dashboard', component: DashboardComponent }
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
                DashboardModule
            ],
            declarations: [SidebarComponent],
            providers: [TranslateService,
                { provide: AuthService, useClass: MockAuthService },
                Location,
                ApplicationsService
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(SidebarComponent);
        component = fixture.componentInstance;
        const auth = fixture.debugElement.injector.get(AuthService);
        auth.admin = admin;
    });

    it('should create Sidebar Component', () => {
        expect(component).toBeTruthy();
    });

    it('should not display Administration Section when user is no admin', (() => {
        admin.next(false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#adminSection'))).toBeNull();
    }));

    it('should display Administration Section when user is admin', (() => {
        admin.next(true);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('#adminSection'))).not.toBeNull();
    }));
});
