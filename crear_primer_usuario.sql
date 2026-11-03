
BEGIN;

INSERT INTO administracion.personal (id_puesto, nombre_personal, ap_paterno, ap_materno, curp)
SELECT pu.id_puesto, 'Dana', 'Constantino', 'Méndez', 'COMD900101MCSNNN09'
FROM administracion.puesto pu
WHERE pu.nombre_puesto = 'ADMINISTRADOR'
AND NOT EXISTS (SELECT 1 FROM administracion.personal WHERE curp = 'COMD900101MCSNNN09');

INSERT INTO administracion.personal_app (id_personal, id_rol, username, password_hash, activo)
SELECT p.id_personal, r.id_rol, 'dana.constantino', '$2a$10$VUEoXI36HomviEAy/bHkJeL7t0mYGrSRnYX93B1yVYmtBbh10Hl7e', TRUE
FROM administracion.personal p
CROSS JOIN administracion.rol r
WHERE p.curp = 'COMD900101MCSNNN09'
AND r.nombre_rol = 'ADMINISTRADOR'
AND NOT EXISTS (SELECT 1 FROM administracion.personal_app WHERE username = 'dana.constantino');

COMMIT;

SELECT username, nombre_completo, nombre_rol, activo FROM administracion.v_cuentas WHERE username = 'dana.constantino';
