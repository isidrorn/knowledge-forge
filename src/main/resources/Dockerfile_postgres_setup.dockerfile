
FROM postgres:latest
ENV POSTGRES_USER=admin
ENV POSTGRES_PASSWORD=admin
ENV POSTGRES_DB=aipipeline
VOLUME /var/lib/postgresql/data
EXPOSE 5432
