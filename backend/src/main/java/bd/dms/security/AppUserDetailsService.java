package bd.dms.security;

import bd.dms.user.AppUser;
import bd.dms.user.UserRepository;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridges the {@link AppUser} table to Spring Security for the password login path.
 * The JWT path does not use this (it trusts the signed role claim); this is only
 * exercised when verifying username + password at login.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return new User(user.getUsername(), user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
