package com.certitrack.config;

import com.certitrack.model.enums.Role;
import java.io.Serializable;

public record SessionIdentity(Long id, Role role) implements Serializable {}
