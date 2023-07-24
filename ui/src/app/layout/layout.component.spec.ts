import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { Location } from '@angular/common';
import { LayoutComponent } from './layout.component';
import { LanguageTranslationModule } from '../shared/modules/language-translation/language-translation.module';
import { PageHeaderModule } from '../shared';
import { AdminModule } from './admin/admin.module';
import { HeaderComponent } from './components/header/header.component';
import { SidebarComponent } from './components/sidebar/sidebar.component';
import { GalapagosToastComponent } from '../shared/modules/toast/toast.component';
import { ToastService } from '../shared/modules/toast/toast.service';
import { EnvironmentsService } from '../shared/services/environments.service';
import { ApplicationsService } from '../shared/services/applications.service';
import { ServerInfoService } from '../shared/services/serverinfo.service';
import { StagingModule } from './staging/staging.module';
import { AuthService } from '../shared/services/auth.service';
import { MockAuthService } from '../shared/util/test-util';

describe('LayoutComponent', () => {
    let component: LayoutComponent;
    let fixture: ComponentFixture<LayoutComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
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
                { provide: AuthService, useClass: MockAuthService },
                Location,
                ToastService,
                EnvironmentsService,
                ApplicationsService,
                ServerInfoService
            ]
        }).compileComponents();

        fixture = TestBed.createComponent(LayoutComponent);
        component = fixture.componentInstance;
    });

    it('should create Layout Component', () => {
        expect(component).toBeTruthy();
    });
});
