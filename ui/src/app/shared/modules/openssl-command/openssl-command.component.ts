import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { copy } from '../../util/copy-util';

@Component({
    selector: 'app-openssl-command',
    templateUrl: './openssl-command.component.html',
    styleUrls: ['./openssl-command.component.scss']
})
export class OpensslCommandComponent implements OnInit, OnChanges {
    @Input() commonName: string;
    @Input() orgUnitName: string;
    @Input() keyfileName: string;
    @Input() generateKey = false;

    commandFormat: 'default' | 'gitbash' = 'default';

    opensslCommand: string;

    copied = false;

    constructor() {
    }

    ngOnInit() {
        this.updateCommand();
    }

    ngOnChanges() {
        this.updateCommand();
    }

    updateCommand() {
        let cmd = '';
        if (this.commandFormat === 'gitbash') {
            cmd += 'MSYS_NO_PATHCONV=1 ';
        }
        cmd += 'openssl req -new -sha256 ';
        if (this.generateKey) {
            cmd += '-nodes -newkey rsa:2048 -keyout';
        } else {
            cmd += '-key';
        }
        cmd += ' ' + this.keyfileName;
        cmd += ' -subj "/CN=' + this.commonName;
        if (this.orgUnitName) {
            cmd += '/OU=' + this.orgUnitName;
        }
        cmd += '"';
        this.opensslCommand = cmd;
    }

    copyCommand() {
        copy(this.opensslCommand);
        this.copied = true;
    }
}
