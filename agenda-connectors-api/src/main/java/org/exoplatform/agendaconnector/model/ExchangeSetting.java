package org.exoplatform.agendaconnector.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExchangeSetting {

    private long id;

    private String domaineName;

    private String username;

    private String password;

}

