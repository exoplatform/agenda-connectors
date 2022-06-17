package org.exoplatform.agendaconnector.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExchangeUserSetting {

  private String domainName;

  private String username;

  private String password;
}
