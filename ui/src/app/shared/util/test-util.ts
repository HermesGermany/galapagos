import { Observable, of } from 'rxjs';
import { UserProfile } from '../services/auth.service';

export class MockAuthService {

    admin: Observable<boolean> = of(false);

    userProfile: Observable<UserProfile> = of({ userName: '', displayName: '' });

    roles: Observable<string[]> = of([]);

    async login(): Promise<boolean> {
        return Promise.resolve(true);
    }

}
