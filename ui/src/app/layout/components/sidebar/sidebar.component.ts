import { Component, EventEmitter, OnInit, Output } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../shared/services/auth.service';
import { Observable } from 'rxjs';

const pushRightClass = 'push-right';

@Component({
    selector: 'app-sidebar',
    templateUrl: './sidebar.component.html',
    styleUrls: ['./sidebar.component.scss']
})
export class SidebarComponent implements OnInit {
    @Output() collapsedEvent = new EventEmitter<boolean>();

    isActive: boolean;
    collapsed: boolean;
    showMenu: string;

    isAdmin: Observable<boolean>;

    constructor(private translate: TranslateService, public router: Router, private authService: AuthService) {
        this.router.events.subscribe(val => {
            if (
                val instanceof NavigationEnd &&
                window.innerWidth <= 992 &&
                this.isToggled()
            ) {
                this.toggleSidebar();
            }
        });
    }

    async ngOnInit() {
        this.isActive = false;
        this.collapsed = false;
        this.showMenu = '';
        this.isAdmin = this.authService.admin;
    }

    toggleCollapsed() {
        this.collapsed = !this.collapsed;
        this.collapsedEvent.emit(this.collapsed);
    }

    isToggled(): boolean {
        const dom: Element = document.querySelector('body');
        return dom.classList.contains(pushRightClass);
    }

    toggleSidebar() {
        const dom: any = document.querySelector('body');
        dom.classList.toggle(pushRightClass);
    }

}
