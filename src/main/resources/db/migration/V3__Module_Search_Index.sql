CREATE TABLE keywords
(
    keyword VARCHAR(255),
    module  VARCHAR(36) REFERENCES modules (moduleid) ON DELETE CASCADE,
    PRIMARY KEY (keyword, module)
);

CREATE MATERIALIZED VIEW module_search_view AS
WITH module_keywords AS (
    SELECT module,
           string_agg(keyword, ' ') AS keyword_text
    FROM keywords
    GROUP BY module
),
     module_exams AS (
         SELECT e.moduleid,
                COUNT(e.examid) AS exam_count,
                array_agg(DISTINCT p.firstname || ' ' || p.lastname) FILTER (WHERE p.professorid IS NOT NULL) AS professors_array,
                string_agg(DISTINCT p.firstname || ' ' || p.lastname, ' ') AS professor_text
         FROM exams e
                  LEFT JOIN professors p ON e.professorid = p.professorid
         GROUP BY e.moduleid
     )
SELECT m.moduleid,
       m.name AS module_name,
       COALESCE(me.exam_count, 0) AS exam_count,
       COALESCE(me.professors_array, '{}') AS professors_array,
       concat_ws(' ', m.name, mk.keyword_text, me.professor_text) AS search_text
FROM modules m
         LEFT JOIN module_keywords mk ON m.moduleid = mk.module
         LEFT JOIN module_exams me    ON m.moduleid = me.moduleid;

CREATE INDEX idx_module_search_trgm ON module_search_view USING gin (search_text gin_trgm_ops);
CREATE UNIQUE INDEX idx_module_search_view_moduleid ON module_search_view (moduleid);