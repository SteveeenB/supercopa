-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE finanzas.comprobantes (
  id uuid NOT NULL,
  aprobado_por character varying,
  cedula_uploader character varying NOT NULL,
  estado character varying NOT NULL CHECK (estado::text = ANY (ARRAY['PENDIENTE_REVISION'::character varying, 'APROBADO'::character varying, 'RECHAZADO'::character varying]::text[])),
  fecha_resolucion timestamp without time zone,
  fecha_subida timestamp without time zone NOT NULL,
  motivo_rechazo character varying,
  referencia_id uuid NOT NULL,
  tipo character varying NOT NULL CHECK (tipo::text = ANY (ARRAY['INSCRIPCION'::character varying, 'MULTA'::character varying]::text[])),
  url_archivo character varying NOT NULL,
  CONSTRAINT comprobantes_pkey PRIMARY KEY (id)
);
CREATE TABLE finanzas.multas (
  id uuid NOT NULL,
  cedula character varying NOT NULL,
  equipo_torneo_id uuid NOT NULL,
  estado character varying NOT NULL CHECK (estado::text = ANY (ARRAY['PENDIENTE'::character varying, 'EN_REVISION'::character varying, 'PAGADA'::character varying, 'CONDONADA'::character varying]::text[])),
  fecha_generacion timestamp without time zone NOT NULL,
  fecha_pago timestamp without time zone,
  monto numeric NOT NULL,
  partido_id uuid NOT NULL,
  partidos_suspension integer NOT NULL,
  tipo_sancion character varying NOT NULL CHECK (tipo_sancion::text = ANY (ARRAY['AMARILLA'::character varying, 'AZUL'::character varying, 'ROJA'::character varying, 'ACUMULACION_AMARILLAS'::character varying, 'ROJA_DIRECTA'::character varying]::text[])),
  torneo_id uuid NOT NULL,
  CONSTRAINT multas_pkey PRIMARY KEY (id)
);