import React from "react";
import { useParams } from "react-router-dom";
import { MainLayout } from "@/components/layout/MainLayout";
import { AdminLessonWizard } from "@/features/admin/wizard/AdminLessonWizard";

const EditLessonPage = () => {
  const { id } = useParams<{ id: string }>();

  return (
    <MainLayout fullScreen>
      <div className="w-full px-3 py-4 md:px-6 md:py-6 xl:px-8 pb-safe">
        <AdminLessonWizard mode="edit" lessonId={id} />
      </div>
    </MainLayout>
  );
};

export default EditLessonPage;
