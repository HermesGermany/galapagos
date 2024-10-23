import { Component, Input } from '@angular/core';
import { ApplicationInfo } from '../../services/applications.service';

@Component({
    selector: 'app-app-link',
    templateUrl: './app-link.component.html',
    styleUrls: ['./app-link.component.scss']
})
export class AppLinkComponent {

    @Input() app: ApplicationInfo;

    @Input() highlightText?: string;

}
