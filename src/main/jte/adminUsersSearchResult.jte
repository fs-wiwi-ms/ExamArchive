@import ms.wiwi.examarchive.model.Role
@import ms.wiwi.examarchive.model.User
@import java.text.SimpleDateFormat
@import java.util.Date
@import java.util.List
@param List<User> users

<table>
    <thead>
    <tr>
        <th scope="col">Vorname</th>
        <th scope="col">Nachname</th>
        <th scope="col">Email</th>
        <th scope="col">Erstellt</th>
        <th scope="col">Letzer Login</th>
        <th scope="col">Typ</th>
        <th scope="col">Aktion</th>
    </tr>
    </thead>
    <tbody>
    @for(User user : users)
    <tr>
        <td>${user.firstname()}</td>
        <td>${user.lastname()}</td>
        <td>${user.email()}</td>
        <td>${new SimpleDateFormat("dd.MM.yyyy").format(Date.from(user.createdAt()))}</td>
        <td>${new SimpleDateFormat("dd.MM.yyyy").format(Date.from(user.lastLogin()))}</td>
        <td>${user.role()}</td>
        @if(user.role() == Role.BLOCKED)
            <td><button style="background-color: #2a7b6f" name="id" value="${user.id()}" hx-post="/admin/users/unblock" hx-swap="outerHTML">Freigeben</button></td>
        @elseif(user.role() == Role.USER)
            <td><button class="primary" style="background-color: var(--pico-del-color)" name="id" value="${user.id()}" hx-post="/admin/users/block" hx-swap="outerHTML">Blockieren</button></td>
        @else
            <td>X</td>
        @endif
    </tr>
    @endfor
    </tbody>
</table>